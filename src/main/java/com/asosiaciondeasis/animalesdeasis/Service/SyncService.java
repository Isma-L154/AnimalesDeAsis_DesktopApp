package com.asosiaciondeasis.animalesdeasis.Service;

import com.asosiaciondeasis.animalesdeasis.Config.FirebaseConfig;
import com.asosiaciondeasis.animalesdeasis.DAO.Animals.AnimalDAO;
import com.asosiaciondeasis.animalesdeasis.DAO.Vaccine.VaccineDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.asosiaciondeasis.animalesdeasis.Util.NetworkUtils;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;

import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;


/**
 * Service class responsible for syncing local SQLite database with Firebase.
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

        //Initialize Firebase only once in here
        FirebaseConfig.initialize();
    }

    /**
     * Public method to trigger the synchronization process.
     * First checks for internet, then pulls remote changes (If there's any of course) and pushes local changes.
     */

    public void sync() {
        if (!NetworkUtils.isInternetAvailable()) {
            System.out.println("No internet connection");
            return;
        }
        try {
            PullChanges();
            PushChanges();
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
        try {
            ApiFuture<QuerySnapshot> query = db.collection("animals").get();
            List<QueryDocumentSnapshot> documents = query.get().getDocuments();

            for (QueryDocumentSnapshot doc : documents) {
                Animal firebaseAnimal = doc.toObject(Animal.class);
                String recordNumber = firebaseAnimal.getRecordNumber();

                Animal localAnimal = animalDAO.findByRecordNumber(recordNumber);

                if (localAnimal == null) {
                    /** Animal does not exist locally → Insert it */
                    firebaseAnimal.setSynced(true);
                    animalDAO.insertAnimal(firebaseAnimal);
                    pullVaccines(doc, recordNumber);
                    System.out.println("⬇ Animal inserted from Firebase: " + recordNumber);

                } else {
                    /** Compare lastModified timestamps to decide whether to update */
                    LocalDateTime firebaseModified = LocalDateTime.parse(firebaseAnimal.getLastModified());
                    LocalDateTime localModified = LocalDateTime.parse(localAnimal.getLastModified());

                    if (firebaseModified.isAfter(localModified)) {

                        firebaseAnimal.setSynced(true);
                        animalDAO.updateAnimal(firebaseAnimal);
                        pullVaccines(doc, recordNumber);
                        System.out.println("🔁 Animal updated from Firebase: " + recordNumber);
                    }
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
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

        /** Get all local animals that are NOT synced */
        List<Animal> unsyncedAnimals = animalDAO.getUnsyncedAnimals();

        for (Animal animal : unsyncedAnimals) {
            DocumentReference animalDoc = db.collection("animals").document(animal.getRecordNumber());
            ApiFuture<WriteResult> writeResult = animalDoc.set(animal);
            writeResult.get(); // Wait for upload to finish

            animal.setSynced(true);
            animalDAO.updateAnimal(animal);
            System.out.println("Animal synced to Firebase: " + animal.getRecordNumber());
        }

        /** Sync ALL unsynced vaccines (from new animals and existing animals) */
        List<Vaccine> allUnsyncedVaccines = vaccineDAO.getAllUnsyncedVaccines();
        for (Vaccine vaccine : allUnsyncedVaccines) {
            DocumentReference animalDoc = db.collection("animals").document(vaccine.getAnimalRecordNumber());
            DocumentReference vaccineDoc = animalDoc.collection("vaccines").document(String.valueOf(vaccine.getId()));
            ApiFuture<WriteResult> vaccineWrite = vaccineDoc.set(vaccine);
            vaccineWrite.get(); // Wait for upload
            vaccine.setSynced(true);
            vaccineDAO.updateVaccine(vaccine);
            System.out.println("⬆ Vaccine synced to Firebase: " + vaccine.getVaccineName() + " for animal: " + vaccine.getAnimalRecordNumber());
        }

        if (unsyncedAnimals.isEmpty() && allUnsyncedVaccines.isEmpty()) {
            System.out.println("Nothing to sync");
        }
    }


    private void pullVaccines(QueryDocumentSnapshot doc, String recordNumber) throws Exception {
        CollectionReference vaccinesRef = doc.getReference().collection("vaccines");
        ApiFuture<QuerySnapshot> vaccineQuery = vaccinesRef.get();
        List<QueryDocumentSnapshot> vaccineDocs = vaccineQuery.get().getDocuments();

        /** Track all vaccine IDs that exist in Firebase for this animal */
        Set<Integer> firebaseIds = new HashSet<>();

        /** Insert or update vaccines from Firebase */
        for (QueryDocumentSnapshot vaccineDoc : vaccineDocs) {
            Vaccine firebaseVaccine = vaccineDoc.toObject(Vaccine.class);
            firebaseVaccine.setAnimalRecordNumber(recordNumber);
            firebaseIds.add(firebaseVaccine.getId());

            Vaccine localVaccine = vaccineDAO.existsVaccine(firebaseVaccine.getId());

            if (localVaccine == null) {
                firebaseVaccine.setSynced(true);
                vaccineDAO.insertVaccine(firebaseVaccine);
                System.out.println("⬇ Vaccine inserted from Firebase: " + firebaseVaccine.getVaccineName());
            } else {
                // Compare timestamps to decide update
                LocalDateTime firebaseModified = LocalDateTime.parse(firebaseVaccine.getLastModified());
                LocalDateTime localModified = LocalDateTime.parse(localVaccine.getLastModified());

                if (firebaseModified.isAfter(localModified)) {
                    firebaseVaccine.setSynced(true);
                    vaccineDAO.updateVaccine(firebaseVaccine);
                    System.out.println("🔁 Vaccine updated from Firebase: " + firebaseVaccine.getVaccineName());
                }
            }
        }

        /** Delete local vaccines that no longer exist in Firebase AND were previously synced */
        List<Vaccine> localVaccines = vaccineDAO.getVaccinesByAnimal(recordNumber);
        for (Vaccine localVaccine : localVaccines) {
            if (!firebaseIds.contains(localVaccine.getId()) && localVaccine.isSynced()) {
                vaccineDAO.deleteVaccine(localVaccine.getId());
                System.out.println("💀 Vaccine deleted locally: " + localVaccine.getVaccineName());
            }
        }
    }

    public void deleteVaccineAndSync(Vaccine vaccine) throws Exception {

        Firestore db = FirestoreClient.getFirestore();

        DocumentReference vaccineDoc = db.collection("animals")
                .document(vaccine.getAnimalRecordNumber())
                .collection("vaccines")
                .document(String.valueOf(vaccine.getId()));

        ApiFuture<WriteResult> deleteFuture = vaccineDoc.delete();
        deleteFuture.get(); // wait for Firebase delete

        // Delete the vaccine from the local database
        vaccineDAO.deleteVaccine(vaccine.getId());
    }

}
