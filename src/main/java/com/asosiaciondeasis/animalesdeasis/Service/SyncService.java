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
import java.util.List;


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
     * Fetches data from Firebase and stores it in the local SQLite database
     * only if it doesn't already exist locally.
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

                    firebaseAnimal.setSynced(true);
                    animalDAO.insertAnimal(firebaseAnimal);
                    pullVaccines(doc, recordNumber);
                    System.out.println("⬇ Animal inserted from Firebase: " + recordNumber);

                } else {

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

        for (QueryDocumentSnapshot vaccineDoc : vaccineDocs) {
            Vaccine vaccine = vaccineDoc.toObject(Vaccine.class);
            vaccine.setAnimalRecordNumber(recordNumber);

            if (!vaccineDAO.existsVaccine(vaccine.getId())) {
                vaccine.setSynced(true);
                vaccineDAO.insertVaccine(vaccine);
            }
        }
    }


}
