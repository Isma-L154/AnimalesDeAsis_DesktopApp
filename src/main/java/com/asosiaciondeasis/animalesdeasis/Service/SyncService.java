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
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Service class responsible for syncing the local SQLite database with Firebase.
 */
public class SyncService {

    private final AnimalDAO animalDAO;
    private final VaccineDAO vaccineDAO;
    private static final DateTimeFormatter DB_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Constructor initializes DAOs with a DB connection obtained from DatabaseConnection.
     * Also initializes Firebase once.
     */
    public SyncService(Connection conn) {

        this.animalDAO = new AnimalDAO(conn);
        this.vaccineDAO = new VaccineDAO(conn);
    }

    /**
     * Main synchronization method that orchestrates the entire sync process.
     * First checks for Firebase availability and internet connectivity.
     * Then performs a two-way sync: pulls remote changes first, then pushes local changes.
     * Finally notifies all registered listeners that sync has completed.
     */
    public void sync() {
        if (!FirebaseConfig.isFirebaseAvailable()) {
            System.out.println("Firebase not available - skipping sync");
            return;
        }
        if (!NetworkUtils.isInternetAvailable()) {
            System.out.println("No internet connection");
            return;
        }
        try {
            PullChanges();
            PushChanges();
            SyncEventManager.notifyListeners();
        } catch (Exception e) {
            System.out.println("Sync process failed -> " + e.getMessage());
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
     *
     * This ensures a local database reflects the most recent state from Firebase.
     */
    private void PullChanges() throws Exception {
        Firestore db = FirestoreClient.getFirestore();

        ApiFuture<QuerySnapshot> query = db.collection("animals").get();
        List<QueryDocumentSnapshot> documents = query.get().getDocuments();

        System.out.println("📥 Encontrados " + documents.size() + " animales en Firebase");

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
                System.out.println("⬇ Animal insertado: " + recordNumber);
            } else if (shouldUpdateFromFirebase(firebaseAnimal, localAnimal)) {
                firebaseAnimal.setSynced(true);
                animalDAO.updateAnimal(firebaseAnimal, false);
                System.out.println("🔁 Animal actualizado: " + recordNumber);
            }

            vaccineFutures.add(doc.getReference().collection("vaccines").get());
            recordNumbers.add(recordNumber);
        }

        List<QuerySnapshot> vaccineSnapshots = ApiFutures.allAsList(vaccineFutures).get();

        for (int i = 0; i < vaccineSnapshots.size(); i++) {
            QuerySnapshot snapshot = vaccineSnapshots.get(i);
            String recordNumber = recordNumbers.get(i);
            pullVaccines(snapshot, recordNumber);
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
     * Uses batch operations to ensure atomicity and improve performance.
     */
    private void PushChanges() throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        WriteBatch batch = db.batch();

        List<Animal> unsyncedAnimals = animalDAO.getUnsyncedAnimals();
        List<Vaccine> allUnsyncedVaccines = vaccineDAO.getAllUnsyncedVaccines();

        for (Animal animal : unsyncedAnimals) {
            DocumentReference animalDoc = db.collection("animals").document(animal.getRecordNumber());
            batch.set(animalDoc, animal);
        }

        for (Vaccine vaccine : allUnsyncedVaccines) {
            DocumentReference vaccineDoc = db.collection("animals")
                    .document(vaccine.getAnimalRecordNumber())
                    .collection("vaccines")
                    .document(vaccine.getId());
            batch.set(vaccineDoc, vaccine);
        }

        if (!unsyncedAnimals.isEmpty() || !allUnsyncedVaccines.isEmpty()) {
            batch.commit().get();

            for (Animal animal : unsyncedAnimals) {
                animal.setSynced(true);
                animalDAO.updateAnimal(animal, false);
            }
            for (Vaccine vaccine : allUnsyncedVaccines) {
                vaccine.setSynced(true);
                vaccineDAO.updateVaccine(vaccine, false);
            }

            System.out.println("⬆ Batch enviado: " + unsyncedAnimals.size() +
                    " animales, " + allUnsyncedVaccines.size() + " vacunas");
        }
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
     * @param recordNumber The animal's record number to associate vaccines with
     */
    private void pullVaccines(QuerySnapshot vaccineSnapshot, String recordNumber) throws Exception {
        Set<String> firebaseVaccineIds = new HashSet<>();

        for (QueryDocumentSnapshot vaccineDoc : vaccineSnapshot.getDocuments()) {
            Vaccine firebaseVaccine = vaccineDoc.toObject(Vaccine.class);
            String vaccineId = vaccineDoc.getId();

            firebaseVaccineIds.add(vaccineId);

            Vaccine localVaccine = vaccineDAO.existsVaccine(vaccineId);

            if (localVaccine == null) {
                firebaseVaccine.setSynced(true);
                vaccineDAO.insertVaccine(firebaseVaccine);
                System.out.println("⬇ Vacuna insertada: " + firebaseVaccine.getVaccineName());
            } else if (shouldUpdateFromFirebaseVaccine(firebaseVaccine, localVaccine)) {
                firebaseVaccine.setSynced(true);
                vaccineDAO.updateVaccine(firebaseVaccine, false);
                System.out.println("🔁 Vacuna actualizada: " + firebaseVaccine.getVaccineName());
            }
        }

        List<Vaccine> localVaccines = vaccineDAO.getVaccinesByAnimal(recordNumber);
        for (Vaccine localVaccine : localVaccines) {
            if (!firebaseVaccineIds.contains(localVaccine.getId()) && localVaccine.isSynced()) {
                vaccineDAO.deleteVaccine(localVaccine.getId());
                System.out.println("🗑 Vacuna eliminada: " + localVaccine.getVaccineName());
            }
        }
    }


    /**
     * Deletes a vaccine from both Firebase and local database in a synchronized manner.
     *
     * Process:
     * 1. If Firebase is available, delete it from Firebase first
     * 2. Only if Firebase deletion succeeds, deletes it from local database
     * 3. If Firebase is unavailable, delete it only locally (will sync on the next connection)
     *
     * This ensures data consistency and handles offline scenarios gracefully.
     *
     * @param vaccine The vaccine object to delete
     * @throws Exception if Firebase deletion fails
     */
    public void deleteVaccineAndSync(Vaccine vaccine) throws Exception {
        if (FirebaseConfig.isFirebaseAvailable()) {
            try {
                Firestore db = FirestoreClient.getFirestore();
                DocumentReference vaccineDoc = db.collection("animals")
                        .document(vaccine.getAnimalRecordNumber())
                        .collection("vaccines")
                        .document(vaccine.getId()); // Now using GUID directly

                ApiFuture<WriteResult> deleteFuture = vaccineDoc.delete();
                deleteFuture.get();

                vaccineDAO.deleteVaccine(vaccine.getId());
            } catch (Exception e) {
                System.out.println("Failed to delete from Firebase: " + e.getMessage());
                throw e;
            }
        } else {
            vaccineDAO.deleteVaccine(vaccine.getId());
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
