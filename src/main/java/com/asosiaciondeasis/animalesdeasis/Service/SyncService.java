package com.asosiaciondeasis.animalesdeasis.Service;

import com.asosiaciondeasis.animalesdeasis.Config.FirebaseConfig;
import com.asosiaciondeasis.animalesdeasis.DAO.Animals.AnimalDAO;
import com.asosiaciondeasis.animalesdeasis.DAO.Vaccine.VaccineDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.asosiaciondeasis.animalesdeasis.Util.NetworkUtils;
import com.asosiaciondeasis.animalesdeasis.Util.SyncEventManager;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;

import java.sql.Connection;
import java.time.LocalDateTime;
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

    /**
     * Constructor initializes DAOs with a DB connection obtained from DatabaseConnection.
     * Also initializes Firebase once.
     */
    public SyncService(Connection conn) {

        this.animalDAO = new AnimalDAO(conn);
        this.vaccineDAO = new VaccineDAO(conn);
    }

    /**
     * Public method to trigger the synchronization process.
     * First checks for internet, then pulls remote changes (If there's any of course) and pushes local changes.
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
     * Pull changes from Firebase and apply them locally.
     * For Animals → If Firebase has newer data, update local.
     * For Vaccines → Also detect deletions in Firebase and remove locally
     * ONLY if the local vaccine was previously synced (avoid deleting new unsynced vaccines).
     */

    private void PullChanges() throws Exception {
        Firestore db = FirestoreClient.getFirestore();
        int processedAnimals = 0;

        try {
            ApiFuture<QuerySnapshot> query = db.collection("animals").get();
            List<QueryDocumentSnapshot> documents = query.get().getDocuments();

            for (QueryDocumentSnapshot doc : documents) {
                try {
                    Animal firebaseAnimal = doc.toObject(Animal.class);
                    String recordNumber = firebaseAnimal.getRecordNumber();

                    if (recordNumber == null || recordNumber.trim().isEmpty()) continue;

                    Animal localAnimal = animalDAO.findByRecordNumber(recordNumber);

                    if (localAnimal == null) {
                        firebaseAnimal.setSynced(true);
                        animalDAO.insertAnimal(firebaseAnimal);
                    } else if (shouldUpdateFromFirebase(firebaseAnimal, localAnimal)) {
                        firebaseAnimal.setSynced(true);
                        animalDAO.updateAnimal(firebaseAnimal, false);
                    }

                    pullVaccines(doc, recordNumber);
                    processedAnimals++;

                } catch (Exception e) {
                    System.out.println("❌ Error procesando animal: " + e.getMessage());
                }
            }

        } catch (Exception e) {
            System.out.println("❌ Error en sincronización: " + e.getMessage());
            throw e;
        }
    }


    /**
     * Uploads local records (animals and vaccines) to Firebase if they are marked as unsynced (synced = 0).
     * After a successful upload, sets the synced flag to 1 in SQLite.

     * Animals → Upload unsynced animals.
     * Vaccines → Upload unsynced vaccines for any animal.
     */
    private void PushChanges() throws Exception {
        Firestore db = FirestoreClient.getFirestore();

        // Sync animals (no changes needed here)
        List<Animal> unsyncedAnimals = animalDAO.getUnsyncedAnimals();
        for (Animal animal : unsyncedAnimals) {
            animal.setSynced(true);
            DocumentReference animalDoc = db.collection("animals").document(animal.getRecordNumber());
            ApiFuture<WriteResult> writeResult = animalDoc.set(animal);
            writeResult.get();

            animalDAO.updateAnimal(animal, false);
            System.out.println("⬆ Animal synced to Firebase: " + animal.getRecordNumber());
        }

        List<Vaccine> allUnsyncedVaccines = vaccineDAO.getAllUnsyncedVaccines();
        for (Vaccine vaccine : allUnsyncedVaccines) {
            try {
                DocumentReference animalDoc = db.collection("animals").document(vaccine.getAnimalRecordNumber());

                // Use the vaccine's GUID as document ID directly
                DocumentReference vaccineDoc = animalDoc.collection("vaccines").document(vaccine.getId());

                vaccine.setSynced(true);
                ApiFuture<WriteResult> vaccineWrite = vaccineDoc.set(vaccine);
                vaccineWrite.get();

                vaccineDAO.updateVaccine(vaccine, false);
                System.out.println("⬆ Vacuna sincronizada: " + vaccine.getVaccineName() + " [ID: " + vaccine.getId() + "]");

            } catch (Exception e) {
                System.out.println("❌ Error sincronizando vacuna: " + e.getMessage());
            }
        }

        if (unsyncedAnimals.isEmpty() && allUnsyncedVaccines.isEmpty()) {
            System.out.println("Nothing to sync");
        }
    }


    private void pullVaccines(QueryDocumentSnapshot doc, String recordNumber) throws Exception {
        CollectionReference vaccinesRef = doc.getReference().collection("vaccines");
        ApiFuture<QuerySnapshot> vaccineQuery = vaccinesRef.get();
        List<QueryDocumentSnapshot> vaccineDocs = vaccineQuery.get().getDocuments();

        Set<String> firebaseVaccineIds = new HashSet<>();

        for (QueryDocumentSnapshot vaccineDoc : vaccineDocs) {
            Vaccine firebaseVaccine = vaccineDoc.toObject(Vaccine.class);
            String vaccineId = vaccineDoc.getId();

            firebaseVaccineIds.add(vaccineId);

            Vaccine localVaccine = vaccineDAO.existsVaccine(vaccineId);

            if (localVaccine == null) {
                Vaccine newVaccine = Vaccine.fromExistingRecord(vaccineId);
                newVaccine.setAnimalRecordNumber(recordNumber);
                newVaccine.setVaccineName(firebaseVaccine.getVaccineName());
                newVaccine.setVaccinationDate(firebaseVaccine.getVaccinationDate());
                newVaccine.setSynced(true);

                vaccineDAO.insertVaccine(newVaccine);
                System.out.println("⬇ Vacuna insertada: " + newVaccine.getVaccineName());
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
    private boolean shouldUpdateFromFirebase(Animal firebaseAnimal, Animal localAnimal) {
        if (firebaseAnimal.getLastModified() == null || localAnimal.getLastModified() == null) {
            return true;
        }

        try {
            LocalDateTime firebaseModified = LocalDateTime.parse(firebaseAnimal.getLastModified());
            LocalDateTime localModified = LocalDateTime.parse(localAnimal.getLastModified());
            return firebaseModified.isAfter(localModified);
        } catch (Exception e) {
            return true;
        }
    }
}
