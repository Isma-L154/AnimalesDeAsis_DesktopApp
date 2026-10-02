package com.asosiaciondeasis.animalesdeasis.Service;

import com.asosiaciondeasis.animalesdeasis.DAO.Animals.AnimalDAO;
import com.asosiaciondeasis.animalesdeasis.DAO.Sync.FirestoreRecords;
import com.asosiaciondeasis.animalesdeasis.DAO.Sync.SyncStateDAO;
import com.asosiaciondeasis.animalesdeasis.DAO.Vaccine.VaccineDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.asosiaciondeasis.animalesdeasis.TestSupport;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Two installations synchronising through a real Firestore, in the local
 * emulator: the same code that runs in the application, end to end.
 *
 * <p>Skipped unless {@code FIRESTORE_EMULATOR_HOST} is set; see
 * {@code FirestoreRecordsEmulatorTest} for how to run it.</p>
 */
class SyncEmulatorTest {

    private record Installation(TestSupport.TestDatabase db, AnimalDAO animals, VaccineDAO vaccines,
                                SyncStateDAO syncState, SyncService sync) {

        static Installation connectedTo(Firestore firestore) throws Exception {
            TestSupport.TestDatabase db = TestSupport.newDatabase();
            TestSupport.seedPlace(db.connection());
            AnimalDAO animals = new AnimalDAO(db.dataSource());
            VaccineDAO vaccines = new VaccineDAO(db.dataSource());
            SyncStateDAO syncState = new SyncStateDAO(db.dataSource());
            return new Installation(db, animals, vaccines, syncState,
                    new SyncService(animals, vaccines, syncState, new FirestoreRecords(() -> firestore)));
        }

        void pullThenPush() throws Exception {
            sync.pullChanges(Instant.now());
            sync.pushChanges();
        }
    }

    @Test
    void changesAndDeletionsTravelBetweenTwoInstallations() throws Exception {
        String host = System.getenv("FIRESTORE_EMULATOR_HOST");
        assumeTrue(host != null, "needs the Firestore emulator");

        try (Firestore firestore = FirestoreOptions.newBuilder()
                .setProjectId("demo-animalesdeasis").setEmulatorHost(host).build().getService()) {
            firestore.recursiveDelete(firestore.collection("animals")).get();
            Installation office = Installation.connectedTo(firestore);
            Installation clinic = Installation.connectedTo(firestore);
            try {
                // Registered at the office, with two vaccines.
                Animal canela = TestSupport.newAnimal(1);
                canela.setName("Canela");
                office.animals.insertAnimal(canela);
                Vaccine rabies = TestSupport.newVaccine(canela.getRecordNumber());
                Vaccine distemper = TestSupport.newVaccine(canela.getRecordNumber());
                office.vaccines.insertVaccine(rabies);
                office.vaccines.insertVaccine(distemper);
                office.pullThenPush();

                clinic.pullThenPush();
                assertEquals("Canela", clinic.animals.findByRecordNumber(canela.getRecordNumber()).getName());
                assertEquals(2, clinic.vaccines.getVaccinesByAnimal(canela.getRecordNumber()).size());
                assertNotNull(clinic.syncState.load().readUpTo());

                // Edited and one vaccine deleted at the clinic; a third added at the office.
                Animal atClinic = clinic.animals.findByRecordNumber(canela.getRecordNumber());
                atClinic.setName("Canela Editada");
                atClinic.setSynced(false);
                clinic.animals.updateAnimal(atClinic);
                try (PreparedStatement pstmt = clinic.db.connection().prepareStatement(
                        "UPDATE animals SET last_modified = '2099-01-01 00:00:00' WHERE record_number = ?")) {
                    pstmt.setString(1, canela.getRecordNumber());
                    pstmt.executeUpdate();
                }
                clinic.vaccines.deleteVaccine(rabies.getId());
                Vaccine parvo = TestSupport.newVaccine(canela.getRecordNumber());
                office.vaccines.insertVaccine(parvo);

                clinic.pullThenPush();
                office.pullThenPush();
                clinic.pullThenPush();

                for (Installation each : new Installation[]{office, clinic}) {
                    assertEquals("Canela Editada",
                            each.animals.findByRecordNumber(canela.getRecordNumber()).getName());
                    assertNull(each.vaccines.findById(rabies.getId()), "deleted at the clinic");
                    assertNotNull(each.vaccines.findById(distemper.getId()));
                    assertNotNull(each.vaccines.findById(parvo.getId()), "added at the office");
                    assertTrue(each.animals.getUnsyncedAnimals().isEmpty());
                    assertTrue(each.vaccines.getAllUnsyncedVaccines().isEmpty());
                    assertTrue(each.vaccines.getPendingDeletions().isEmpty());
                    assertFalse(SyncService.needsFullPull(each.syncState.load(), Instant.now()),
                            "from here on, pulls are incremental");
                }
            } finally {
                office.db.close();
                clinic.db.close();
            }
        }
    }
}
