package com.asosiaciondeasis.animalesdeasis.Service;

import com.asosiaciondeasis.animalesdeasis.Config.FirebaseConfig;
import com.asosiaciondeasis.animalesdeasis.DAO.Animals.AnimalDAO;
import com.asosiaciondeasis.animalesdeasis.DAO.Vaccine.VaccineDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.asosiaciondeasis.animalesdeasis.Util.NetworkUtils;
import com.asosiaciondeasis.animalesdeasis.Util.SyncEventManager;
import com.google.api.core.ApiFuture;
import com.google.api.core.ApiFutures;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteBatch;
import com.google.firebase.cloud.FirestoreClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

/**
 * Two-way synchronisation between the local SQLite database and Firestore.
 *
 * <p>Pull runs before push, and the newer {@code last_modified} wins on either
 * side, so an edit made offline is not overwritten by an older remote copy.</p>
 */
public class SyncService {
    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private static final DateTimeFormatter DB_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Firestore commits at most 500 operations in one batch. Exceeding it fails
     * the whole commit, so the more work had accumulated offline, the more
     * certain it was that none of it would upload.
     */
    private static final int MAX_BATCH_OPERATIONS = 500;

    /**
     * One synchronisation at a time, across every instance. The scheduler and the
     * startup sync can overlap, and both read-modify-write the same rows through
     * the shared connection: two concurrent runs could each push a record and then
     * mark the other's pull as synced.
     */
    private static final ReentrantLock SYNC_LOCK = new ReentrantLock();

    private final AnimalDAO animalDAO;
    private final VaccineDAO vaccineDAO;

    public SyncService(Connection conn) {
        this.animalDAO = new AnimalDAO(conn);
        this.vaccineDAO = new VaccineDAO(conn);
    }

    /**
     * Pulls remote changes, pushes local ones, then notifies listeners. Returns
     * without doing anything when Firebase or the network is unavailable, or when
     * another synchronisation is already running.
     */
    public void sync() {
        if (!FirebaseConfig.isFirebaseAvailable()) {
            log.info("Firebase not available - skipping sync");
            return;
        }
        if (!NetworkUtils.isInternetAvailable()) {
            log.info("No internet connection - skipping sync");
            return;
        }
        if (!SYNC_LOCK.tryLock()) {
            log.info("A sync is already running - skipping this one");
            return;
        }
        try {
            pullChanges();
            pushChanges();
            SyncEventManager.notifyListeners();
        } catch (Exception e) {
            log.warn("Sync failed; unsynced records stay queued for the next run", e);
        } finally {
            SYNC_LOCK.unlock();
        }
    }

    /**
     * Downloads and applies changes from Firebase to the local database.
     *
     * Process:
     * 1. Fetches all animals from Firebase "animals" collection
     * 2. For each animal, compare with a local version using lastModified timestamp
     * 3. If a Firebase version is newer or animal doesn't exist locally, updates/inserts locally
     * 4. Simultaneously fetches all vaccines for each animal using batch requests
     * 5. Applies vaccine changes including deletions (only for previously synced vaccines)
     */
    private void pullChanges() throws Exception {
        Firestore db = FirestoreClient.getFirestore();

        ApiFuture<QuerySnapshot> query = db.collection("animals").get();
        List<QueryDocumentSnapshot> documents = query.get().getDocuments();

        log.info("Found {} animals in Firebase", documents.size());

        List<ApiFuture<QuerySnapshot>> vaccineFutures = new ArrayList<>();
        List<String> recordNumbers = new ArrayList<>();

        for (QueryDocumentSnapshot doc : documents) {
            Animal firebaseAnimal = doc.toObject(Animal.class);
            String recordNumber = firebaseAnimal.getRecordNumber();
            if (recordNumber == null || recordNumber.trim().isEmpty()) continue;

            Animal localAnimal = animalDAO.findByRecordNumber(recordNumber);

            if (localAnimal == null) {
                firebaseAnimal.setSynced(true);
                animalDAO.insertAnimal(firebaseAnimal);
                log.info("Animal inserted from Firebase: {}", recordNumber);
            } else if (shouldUpdateFromFirebase(firebaseAnimal, localAnimal)) {
                firebaseAnimal.setSynced(true);
                animalDAO.updateAnimal(firebaseAnimal, false);
                log.info("Animal updated from Firebase: {}", recordNumber);
            }

            vaccineFutures.add(doc.getReference().collection("vaccines").get());
            recordNumbers.add(recordNumber);
        }

        List<QuerySnapshot> vaccineSnapshots = ApiFutures.allAsList(vaccineFutures).get();

        // Deletions made here that have not reached Firebase yet. Without this the
        // pull sees the row still present remotely, finds nothing locally, and
        // helpfully puts it back - undoing the deletion the user made offline.
        // Read once per pull rather than once per animal.
        Set<String> deletedHere = new HashSet<>(vaccineDAO.getPendingDeletions());

        for (int i = 0; i < vaccineSnapshots.size(); i++) {
            pullVaccines(vaccineSnapshots.get(i), recordNumbers.get(i), deletedHere);
        }
    }

    /**
     * Uploads local unsynced changes to Firebase using batch operations.
     *
     * Process:
     * 1. Retrieve all animals marked as unsynced (synced = false)
     * 2. Retrieves all vaccines marked as unsynced across all animals
     * 3. Creates a Firebase batch operation for efficient bulk upload
     * 4. Uploads animals to "animals" collection
     * 5. Uploads vaccines to "animals/{recordNumber}/vaccines" subcollections
     * 6. After successful upload, marks all uploaded records as synced locally
     *
     * Uses batch operations to reduce round trips.
     */
    private void pushChanges() throws Exception {
        Firestore db = FirestoreClient.getFirestore();

        List<Animal> unsyncedAnimals = animalDAO.getUnsyncedAnimals();
        List<Vaccine> allUnsyncedVaccines = vaccineDAO.getAllUnsyncedVaccines();
        List<String> pendingDeletions = vaccineDAO.getPendingDeletions();

        if (unsyncedAnimals.isEmpty() && allUnsyncedVaccines.isEmpty() && pendingDeletions.isEmpty()) {
            return;
        }

        // Every write is queued as an operation, then committed in chunks. This
        // used to be a single WriteBatch holding everything, which fails outright
        // past Firestore's limit of 500 operations - so the more work had piled
        // up offline, the more certain it was that none of it would upload. The
        // failure was silent, too: sync() logged the exception and returned.
        // Each entry applies itself to whichever batch it is handed, so no
        // shared mutable state is needed to assemble the chunks.
        List<Consumer<WriteBatch>> pendingWrites = new ArrayList<>();

        for (Animal animal : unsyncedAnimals) {
            pendingWrites.add(batch -> batch.set(
                    db.collection("animals").document(animal.getRecordNumber()), animal));
        }
        for (Vaccine vaccine : allUnsyncedVaccines) {
            pendingWrites.add(batch -> batch.set(
                    vaccineDocument(db, vaccine.getAnimalRecordNumber(), vaccine.getId()), vaccine));
        }
        // Deletions travel with the rest. Applying them in the same pass is what
        // keeps a record deleted offline from coming back on the next pull.
        for (String vaccineId : pendingDeletions) {
            String animalRecordNumber = vaccineDAO.getPendingDeletionAnimal(vaccineId);
            if (animalRecordNumber != null) {
                pendingWrites.add(batch ->
                        batch.delete(vaccineDocument(db, animalRecordNumber, vaccineId)));
            }
        }

        commitInChunks(db, pendingWrites);

        // Marked only after the commit that carried them succeeded. Marking first
        // would lose the change permanently if the commit then failed.
        for (Animal animal : unsyncedAnimals) {
            animal.setSynced(true);
            animalDAO.updateAnimal(animal, false);
        }
        for (Vaccine vaccine : allUnsyncedVaccines) {
            vaccine.setSynced(true);
            vaccineDAO.updateVaccine(vaccine, false);
        }
        for (String vaccineId : pendingDeletions) {
            vaccineDAO.clearPendingDeletion(vaccineId);
        }

        log.info("Pushed {} animals, {} vaccines, {} deletions",
                unsyncedAnimals.size(), allUnsyncedVaccines.size(), pendingDeletions.size());
    }

    private static DocumentReference vaccineDocument(Firestore db, String animalRecordNumber,
                                                     String vaccineId) {
        return db.collection("animals")
                .document(animalRecordNumber)
                .collection("vaccines")
                .document(vaccineId);
    }

    /**
     * Commits queued writes in batches no larger than Firestore allows.
     *
     * <p>Chunks are committed in order and each one is awaited, so a failure
     * halfway leaves the earlier chunks applied and the rest still marked
     * unsynced locally. That is deliberate: the alternative is losing everything
     * because of one bad record, and the next run simply picks up where this one
     * stopped. Synchronisation here is idempotent - every write is a
     * {@code set()} on a known document id.</p>
     */
    private void commitInChunks(Firestore db, List<Consumer<WriteBatch>> writes)
            throws Exception {
        for (List<Consumer<WriteBatch>> chunk
                : partition(writes, MAX_BATCH_OPERATIONS)) {
            WriteBatch batch = db.batch();
            for (Consumer<WriteBatch> write : chunk) {
                write.accept(batch);
            }
            batch.commit().get();
        }
    }

    /**
     * Splits {@code items} into consecutive groups of at most {@code size}.
     *
     * <p>Separated out and package-visible so the boundaries can be tested
     * without Firestore. Off-by-one here is the whole bug: one operation over the
     * limit and the commit fails entirely.</p>
     */
    static <T> List<List<T>> partition(List<T> items, int size) {
        if (size < 1) {
            throw new IllegalArgumentException("chunk size must be positive, got " + size);
        }
        List<List<T>> chunks = new ArrayList<>();
        for (int start = 0; start < items.size(); start += size) {
            chunks.add(new ArrayList<>(items.subList(start, Math.min(start + size, items.size()))));
        }
        return chunks;
    }

    /**
     * Handles vaccine synchronization for a specific animal.
     *
     * This method performs bidirectional vaccine sync:
     * - Downloads new vaccines from Firebase and adds them locally
     * - Identifies vaccines that were deleted in Firebase and removes them locally
     *   (only removes previously synced vaccines to avoid deleting new local vaccines)
     *
     * @param vaccineSnapshot Firebase query result containing vaccines for an animal
     * @param recordNumber    the animal the vaccines belong to
     * @param deletedHere     vaccine ids deleted locally and not yet pushed
     */
    private void pullVaccines(QuerySnapshot vaccineSnapshot, String recordNumber,
                              Set<String> deletedHere) throws Exception {
        Set<String> firebaseVaccineIds = new HashSet<>();

        for (QueryDocumentSnapshot vaccineDoc : vaccineSnapshot.getDocuments()) {
            Vaccine firebaseVaccine = vaccineDoc.toObject(Vaccine.class);
            String vaccineId = vaccineDoc.getId();

            firebaseVaccineIds.add(vaccineId);

            if (deletedHere.contains(vaccineId)) {
                continue;
            }

            Vaccine localVaccine = vaccineDAO.existsVaccine(vaccineId);

            if (localVaccine == null) {
                firebaseVaccine.setSynced(true);
                vaccineDAO.insertVaccine(firebaseVaccine);
                log.info("Vaccine inserted from Firebase: {}", vaccineId);
            } else if (shouldUpdateFromFirebaseVaccine(firebaseVaccine, localVaccine)) {
                firebaseVaccine.setSynced(true);
                vaccineDAO.updateVaccine(firebaseVaccine, false);
                log.info("Vaccine updated from Firebase: {}", vaccineId);
            }
        }

        List<Vaccine> localVaccines = vaccineDAO.getVaccinesByAnimal(recordNumber);
        for (Vaccine localVaccine : localVaccines) {
            if (!firebaseVaccineIds.contains(localVaccine.getId()) && localVaccine.isSynced()) {
                vaccineDAO.deleteVaccine(localVaccine.getId());
                log.info("Vaccine removed, deleted in Firebase: {}", localVaccine.getId());
            }
        }
    }

    /**
     * Deletes a vaccine locally, then tries to delete it remotely.
     *
     * <p>Blocks on the network: call it from a background thread.</p>
     *
     * @throws Exception only when the local deletion fails; a remote failure is
     *         left to the next sync, which the tombstone guarantees
     */
    public void deleteVaccineAndSync(Vaccine vaccine) throws Exception {
        // Delete locally first, which also writes the tombstone. The record then
        // cannot come back regardless of what happens next: if the remote delete
        // fails, or there is no connection at all, the tombstone keeps the
        // deletion pending until a later sync applies it.
        //
        // The previous order was the other way round - remote first, local only
        // if that succeeded - so a failure left the vaccine present locally while
        // the user had been told it was gone.
        vaccineDAO.deleteVaccine(vaccine.getId());

        if (!FirebaseConfig.isFirebaseAvailable()) {
            return;
        }
        try {
            Firestore db = FirestoreClient.getFirestore();
            vaccineDocument(db, vaccine.getAnimalRecordNumber(), vaccine.getId()).delete().get();
            vaccineDAO.clearPendingDeletion(vaccine.getId());
        } catch (Exception e) {
            // Not rethrown. The deletion has happened as far as the user is
            // concerned, and the tombstone guarantees it reaches Firebase
            // eventually. Failing here would report an error for something that
            // succeeded.
            log.warn("Remote delete of vaccine {} failed; the next sync will retry it",
                    vaccine.getId(), e);
        }
    }

    /**
     * Determines whether the local animal record should be updated with Firebase data.
     *
     * Uses lastModified timestamps to compare versions:
     * - If either timestamp is null, defaults to updating (safe fallback)
     * - If a Firebase version has a more recent timestamp, returns true
     * - If timestamp parsing fails, defaults to updating (safe fallback)
     *
     * This prevents overwriting newer local changes with older Firebase data.
     */
    private boolean shouldUpdateFromFirebaseTimestamp(String fbTime, String localTime) {
        if (fbTime == null || localTime == null) return true;
        try {
            LocalDateTime firebaseModified = LocalDateTime.parse(fbTime, DB_FORMATTER);
            LocalDateTime localModified = LocalDateTime.parse(localTime, DB_FORMATTER);
            return firebaseModified.isAfter(localModified);
        } catch (Exception e) {
            return true;
        }
    }

    private boolean shouldUpdateFromFirebase(Animal firebaseAnimal, Animal localAnimal) {
        return shouldUpdateFromFirebaseTimestamp(firebaseAnimal.getLastModified(), localAnimal.getLastModified());
    }

    private boolean shouldUpdateFromFirebaseVaccine(Vaccine firebaseVaccine, Vaccine localVaccine) {
        return shouldUpdateFromFirebaseTimestamp(firebaseVaccine.getLastModified(), localVaccine.getLastModified());
    }
}
