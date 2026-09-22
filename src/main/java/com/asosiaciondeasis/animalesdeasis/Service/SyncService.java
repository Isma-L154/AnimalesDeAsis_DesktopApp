package com.asosiaciondeasis.animalesdeasis.Service;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.RowVersion;
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

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Two-way synchronisation between the local SQLite database and Firestore.
 *
 * <p>Pull runs before push, and the newer {@code last_modified} wins on either
 * side, so an edit made offline is not overwritten by an older remote copy.
 * Local reads happen once per pass and local writes are applied in batched
 * transactions, each of which re-checks that the row has not been edited since
 * it was read.</p>
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
     * startup sync can overlap, and both read-modify-write the same rows.
     */
    private static final ReentrantLock SYNC_LOCK = new ReentrantLock();

    private final AnimalDAO animalDAO;
    private final VaccineDAO vaccineDAO;

    public SyncService(DataSource dataSource) {
        this.animalDAO = new AnimalDAO(dataSource);
        this.vaccineDAO = new VaccineDAO(dataSource);
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
        try {
            boolean ran = runExclusively(() -> {
                pullChanges();
                pushChanges();
                SyncEventManager.notifyListeners();
            });
            if (!ran) {
                log.info("A sync is already running - skipping this one");
            }
        } catch (Exception e) {
            log.warn("Sync failed; unsynced records stay queued for the next run", e);
        }
    }

    @FunctionalInterface
    interface SyncStep {
        void run() throws Exception;
    }

    /**
     * Runs {@code step} unless another synchronisation holds the lock.
     *
     * @return whether it ran
     */
    static boolean runExclusively(SyncStep step) throws Exception {
        if (!SYNC_LOCK.tryLock()) {
            return false;
        }
        try {
            step.run();
            return true;
        } finally {
            SYNC_LOCK.unlock();
        }
    }

    /**
     * Downloads every animal and its vaccines, and applies whatever is new or
     * newer than the local copy. Vaccines deleted remotely are removed locally
     * unless they were edited here since their last sync.
     */
    private void pullChanges() throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        List<QueryDocumentSnapshot> documents = db.collection("animals").get().get().getDocuments();
        log.info("Found {} animals in Firebase", documents.size());

        List<Animal> remoteAnimals = new ArrayList<>();
        List<ApiFuture<QuerySnapshot>> vaccineFutures = new ArrayList<>();
        for (QueryDocumentSnapshot doc : documents) {
            Animal animal = doc.toObject(Animal.class);
            if (animal.getRecordNumber() == null || animal.getRecordNumber().isBlank()) {
                continue;
            }
            animal.setSynced(true);
            remoteAnimals.add(animal);
            vaccineFutures.add(doc.getReference().collection("vaccines").get());
        }

        Map<String, RowVersion> localAnimals = animalDAO.getRowVersions();
        List<Animal> animalChanges = newerThanLocal(remoteAnimals, Animal::getRecordNumber,
                Animal::getLastModified, localAnimals);
        // Animals first: the vaccines below reference them.
        animalDAO.saveFromRemote(animalChanges, localAnimals);

        List<QuerySnapshot> vaccineSnapshots = ApiFutures.allAsList(vaccineFutures).get();
        pullVaccines(remoteAnimals, vaccineSnapshots);

        log.info("Applied {} animals from Firebase", animalChanges.size());
    }

    /**
     * @param remoteAnimals    the animals whose vaccines were fetched
     * @param vaccineSnapshots their vaccine subcollections, in the same order
     */
    private void pullVaccines(List<Animal> remoteAnimals, List<QuerySnapshot> vaccineSnapshots) throws Exception {
        // Deletions made here that have not reached Firebase yet. Without this the
        // pull sees the row still present remotely, finds nothing locally, and
        // helpfully puts it back - undoing the deletion the user made offline.
        Set<String> deletedHere = vaccineDAO.getPendingDeletions().keySet();

        Map<String, RowVersion> localVersions = new HashMap<>();
        Map<String, List<Vaccine>> localByAnimal = new HashMap<>();
        for (Vaccine vaccine : vaccineDAO.getAllVaccines()) {
            localVersions.put(vaccine.getId(), new RowVersion(vaccine.getLastModified(), vaccine.isSynced()));
            localByAnimal.computeIfAbsent(vaccine.getAnimalRecordNumber(), k -> new ArrayList<>()).add(vaccine);
        }

        List<Vaccine> remoteVaccines = new ArrayList<>();
        Map<String, RowVersion> removedRemotely = new HashMap<>();
        for (int i = 0; i < remoteAnimals.size(); i++) {
            String recordNumber = remoteAnimals.get(i).getRecordNumber();
            Set<String> remoteIds = new HashSet<>();
            for (QueryDocumentSnapshot doc : vaccineSnapshots.get(i).getDocuments()) {
                remoteIds.add(doc.getId());
                if (deletedHere.contains(doc.getId())) {
                    continue;
                }
                Vaccine vaccine = doc.toObject(Vaccine.class);
                vaccine.setSynced(true);
                remoteVaccines.add(vaccine);
            }
            for (Vaccine local : localByAnimal.getOrDefault(recordNumber, List.of())) {
                // Only previously synced vaccines: an unsynced one is new here and
                // simply has not been pushed yet.
                if (local.isSynced() && !remoteIds.contains(local.getId())) {
                    removedRemotely.put(local.getId(), localVersions.get(local.getId()));
                }
            }
        }

        List<Vaccine> vaccineChanges = newerThanLocal(remoteVaccines, Vaccine::getId,
                Vaccine::getLastModified, localVersions);
        vaccineDAO.saveFromRemote(vaccineChanges, localVersions);
        vaccineDAO.deleteRemovedRemotely(removedRemotely);

        log.info("Applied {} vaccines from Firebase, removed {} deleted there",
                vaccineChanges.size(), removedRemotely.size());
    }

    /**
     * The remote records worth applying: those missing locally, and those whose
     * timestamp is newer than the local one.
     */
    static <T> List<T> newerThanLocal(List<T> remote, Function<T, String> id,
                                      Function<T, String> lastModified, Map<String, RowVersion> local) {
        List<T> changes = new ArrayList<>();
        for (T record : remote) {
            String key = id.apply(record);
            if (!local.containsKey(key)
                    || isNewer(lastModified.apply(record), local.get(key).lastModified())) {
                changes.add(record);
            }
        }
        return changes;
    }

    /**
     * Whether the remote timestamp is newer. A missing or unreadable timestamp
     * counts as newer, so a malformed record is repaired from the remote copy
     * rather than left diverged forever.
     */
    static boolean isNewer(String remoteTime, String localTime) {
        if (remoteTime == null || localTime == null) {
            return true;
        }
        try {
            return LocalDateTime.parse(remoteTime, DB_FORMATTER)
                    .isAfter(LocalDateTime.parse(localTime, DB_FORMATTER));
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Uploads local changes and deletions in batches, then marks what was
     * uploaded as synced - only rows still identical to what was sent, so an
     * edit saved during the upload is pushed next time rather than lost.
     */
    private void pushChanges() throws Exception {
        Firestore db = FirestoreClient.getFirestore();

        List<Animal> unsyncedAnimals = animalDAO.getUnsyncedAnimals();
        List<Vaccine> unsyncedVaccines = vaccineDAO.getAllUnsyncedVaccines();
        Map<String, String> pendingDeletions = vaccineDAO.getPendingDeletions();

        if (unsyncedAnimals.isEmpty() && unsyncedVaccines.isEmpty() && pendingDeletions.isEmpty()) {
            return;
        }

        // Each entry applies itself to whichever batch it is handed, so no shared
        // mutable state is needed to assemble the chunks.
        List<Consumer<WriteBatch>> pendingWrites = new ArrayList<>();
        for (Animal animal : unsyncedAnimals) {
            pendingWrites.add(batch -> batch.set(
                    db.collection("animals").document(animal.getRecordNumber()), animal));
        }
        for (Vaccine vaccine : unsyncedVaccines) {
            pendingWrites.add(batch -> batch.set(
                    vaccineDocument(db, vaccine.getAnimalRecordNumber(), vaccine.getId()), vaccine));
        }
        // Deletions travel with the rest. Applying them in the same pass is what
        // keeps a record deleted offline from coming back on the next pull.
        pendingDeletions.forEach((vaccineId, animalRecordNumber) -> pendingWrites.add(batch ->
                batch.delete(vaccineDocument(db, animalRecordNumber, vaccineId))));

        commitInChunks(db, pendingWrites);

        // Marked only after the commit that carried them succeeded. Marking first
        // would lose the change permanently if the commit then failed.
        int animalsMarked = animalDAO.markSynced(unsyncedAnimals);
        int vaccinesMarked = vaccineDAO.markSynced(unsyncedVaccines);
        vaccineDAO.clearPendingDeletions(pendingDeletions.keySet());

        log.info("Pushed {} animals, {} vaccines, {} deletions ({} animals and {} vaccines were edited "
                        + "during the upload and stay queued)",
                unsyncedAnimals.size(), unsyncedVaccines.size(), pendingDeletions.size(),
                unsyncedAnimals.size() - animalsMarked, unsyncedVaccines.size() - vaccinesMarked);
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
    private void commitInChunks(Firestore db, List<Consumer<WriteBatch>> writes) throws Exception {
        for (List<Consumer<WriteBatch>> chunk : partition(writes, MAX_BATCH_OPERATIONS)) {
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
        vaccineDAO.deleteVaccine(vaccine.getId());

        if (!FirebaseConfig.isFirebaseAvailable()) {
            return;
        }
        try {
            Firestore db = FirestoreClient.getFirestore();
            vaccineDocument(db, vaccine.getAnimalRecordNumber(), vaccine.getId()).delete().get();
            vaccineDAO.clearPendingDeletions(List.of(vaccine.getId()));
        } catch (Exception e) {
            // Not rethrown. The deletion has happened as far as the user is
            // concerned, and the tombstone guarantees it reaches Firebase
            // eventually. Failing here would report an error for something that
            // succeeded.
            log.warn("Remote delete of vaccine {} failed; the next sync will retry it",
                    vaccine.getId(), e);
        }
    }
}
