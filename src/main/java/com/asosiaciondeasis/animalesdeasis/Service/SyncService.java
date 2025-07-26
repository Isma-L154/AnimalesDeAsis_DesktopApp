package com.asosiaciondeasis.animalesdeasis.Service;

import com.asosiaciondeasis.animalesdeasis.Config.DatabaseConnection;
import com.asosiaciondeasis.animalesdeasis.DAO.Animals.AnimalDAO;
import com.asosiaciondeasis.animalesdeasis.DAO.Vaccine.VaccineDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.asosiaciondeasis.animalesdeasis.Util.NetworkUtils;
import com.asosiaciondeasis.animalesdeasis.Config.FirebaseConfig;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import com.google.firebase.cloud.FirestoreClient;

import java.sql.Connection;
import java.sql.SQLException;
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

    public void sync(){
        if (!NetworkUtils.isInternetAvailable()){
            System.out.println("No internet connection");
            return;
        }
        try{
            PullChanges();
            PushChanges();
        }catch(Exception e){
            System.out.println("Sync process failed -> " + e.getMessage());
        }
    }

    /**
     * Fetches data from Firebase and stores it in the local SQLite database
     * only if it doesn't already exist locally.
     */
    private void PullChanges() throws Exception{
        Firestore db = FirestoreClient.getFirestore();
        try{
            /** Get the animals from the DB (FireBase) */
            ApiFuture<QuerySnapshot> query = db.collection("animals").get();
            List<QueryDocumentSnapshot> documents = query.get().getDocuments();

            for(QueryDocumentSnapshot doc : documents){
                Animal animal = doc.toObject(Animal.class);

                /** Check if animal exists locally by recordNumber or chipNumber */
                Animal localAnimal = animalDAO.findByChipNumber(animal.getChipNumber());
                if (localAnimal ==  null){
                    animal.setSynced(true);
                    animalDAO.insertAnimal(animal);

                    /** Pull vaccines subcollection for this animal */
                    CollectionReference vaccinesRef = doc.getReference().collection("vaccines");
                    ApiFuture<QuerySnapshot> vaccineQuery = vaccinesRef.get();
                    List<QueryDocumentSnapshot> vaccineDocs = vaccineQuery.get().getDocuments();

                    for (QueryDocumentSnapshot vaccineDoc : vaccineDocs) {
                        Vaccine vaccine = vaccineDoc.toObject(Vaccine.class);
                        vaccine.setAnimalRecordNumber(animal.getRecordNumber());
                        vaccine.setSynced(true);
                        vaccineDAO.insertVaccine(vaccine);
                    }
                    System.out.println("⬇ Animal synced from Firebase: " + animal.getRecordNumber());
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
    private void PushChanges() throws Exception{
        Firestore db = FirestoreClient.getFirestore();

        /** Get all local animals that are NOT synced */
        List<Animal> unsyncedAnimals = animalDAO.getUnsyncedAnimals();
        if (unsyncedAnimals.isEmpty()){
            System.out.println("Nothing to sync");
        }
        for(Animal animal : unsyncedAnimals){
            DocumentReference animalDoc = db.collection("animals").document(animal.getRecordNumber());
            ApiFuture<WriteResult> writeResult = animalDoc.set(animal);
            writeResult.get(); // Wait for upload to finish

            /** Upload related vaccines for this animal */
            List<Vaccine> unsyncedVaccines = vaccineDAO.getUnsyncedVaccinesByAnimal(animal.getRecordNumber());
            for (Vaccine vaccine : unsyncedVaccines) {
                DocumentReference vaccineDoc = animalDoc.collection("vaccines").document(String.valueOf(vaccine.getId()));
                ApiFuture<WriteResult> vaccineWrite = vaccineDoc.set(vaccine);
                vaccineWrite.get(); // Wait for upload
                vaccine.setSynced(true);
                vaccineDAO.updateVaccine(vaccine);
            }
            animal.setSynced(true);
            animalDAO.updateAnimal(animal);
            System.out.println("Animal synced to Firebase: " + animal.getRecordNumber());
        }
    }
}
