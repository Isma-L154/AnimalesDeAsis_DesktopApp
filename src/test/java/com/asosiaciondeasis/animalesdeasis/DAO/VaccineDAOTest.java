package com.asosiaciondeasis.animalesdeasis.DAO;

import com.asosiaciondeasis.animalesdeasis.DAO.Animals.AnimalDAO;
import com.asosiaciondeasis.animalesdeasis.DAO.Vaccine.VaccineDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.asosiaciondeasis.animalesdeasis.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class VaccineDAOTest {

    private TestSupport.TestDatabase db;
    private Connection conn;
    private VaccineDAO vaccineDAO;
    private Animal animal;

    @BeforeEach
    void setUp() throws Exception {
        db = TestSupport.newDatabase();
        conn = db.connection();
        int placeId = TestSupport.seedPlace(conn);
        AnimalDAO animalDAO = new AnimalDAO(db.dataSource());
        animal = TestSupport.newAnimal(placeId);
        animalDAO.insertAnimal(animal);
        vaccineDAO = new VaccineDAO(db.dataSource());
    }

    @AfterEach
    void tearDown() throws Exception {
        db.close();
    }

    @Test
    void insertAndRetrieveVaccine() throws Exception {
        Vaccine vaccine = TestSupport.newVaccine(animal.getRecordNumber());
        vaccineDAO.insertVaccine(vaccine);

        List<Vaccine> vaccines = vaccineDAO.getVaccinesByAnimal(animal.getRecordNumber());
        assertEquals(1, vaccines.size());
        assertEquals("Rabia", vaccines.get(0).getVaccineName());
    }

    @Test
    void existsVaccineReturnsNullWhenAbsent() throws Exception {
        assertNull(vaccineDAO.findById("does-not-exist"));
    }

    @Test
    void updateVaccineChangesFields() throws Exception {
        Vaccine vaccine = TestSupport.newVaccine(animal.getRecordNumber());
        vaccineDAO.insertVaccine(vaccine);

        vaccine.setVaccineName("Moquillo");
        vaccineDAO.updateVaccine(vaccine);

        assertEquals("Moquillo", vaccineDAO.findById(vaccine.getId()).getVaccineName());
    }

    @Test
    void remoteVaccinesAreInsertedAndReplaced() throws Exception {
        Vaccine existing = TestSupport.newVaccine(animal.getRecordNumber());
        existing.setLastModified("2020-01-01 00:00:00");
        vaccineDAO.insertVaccine(existing);

        Vaccine newer = vaccineDAO.findById(existing.getId());
        newer.setVaccineName("Moquillo");
        newer.setLastModified("2021-01-01 00:00:00");
        Vaccine brandNew = TestSupport.newVaccine(animal.getRecordNumber());
        brandNew.setLastModified(null);

        vaccineDAO.saveFromRemote(List.of(newer, brandNew), Map.of(existing.getId(), "2020-01-01 00:00:00"));

        assertEquals("Moquillo", vaccineDAO.findById(existing.getId()).getVaccineName());
        assertEquals("2021-01-01 00:00:00", vaccineDAO.findById(existing.getId()).getLastModified());
        assertNotNull(vaccineDAO.findById(brandNew.getId()).getLastModified(),
                "a missing remote timestamp is stamped instead of breaking NOT NULL");
    }

    @Test
    void remoteVaccineDoesNotOverwriteAnEditMadeAfterTheComparison() throws Exception {
        Vaccine vaccine = TestSupport.newVaccine(animal.getRecordNumber());
        vaccine.setLastModified("2020-01-01 00:00:00");
        vaccineDAO.insertVaccine(vaccine);

        vaccine.setVaccineName("Editada aquí");
        vaccineDAO.updateVaccine(vaccine);

        Vaccine remote = vaccineDAO.findById(vaccine.getId());
        remote.setVaccineName("Versión remota");
        remote.setLastModified("2021-01-01 00:00:00");
        vaccineDAO.saveFromRemote(List.of(remote), Map.of(vaccine.getId(), "2020-01-01 00:00:00"));

        assertEquals("Editada aquí", vaccineDAO.findById(vaccine.getId()).getVaccineName());
    }

    @Test
    void markSyncedLeavesAnEditMadeDuringTheUploadQueued() throws Exception {
        Vaccine vaccine = TestSupport.newVaccine(animal.getRecordNumber());
        vaccineDAO.insertVaccine(vaccine);
        List<Vaccine> pushed = vaccineDAO.getAllUnsyncedVaccines();

        Vaccine edited = vaccineDAO.findById(vaccine.getId());
        edited.setVaccineName("Editada durante la subida");
        vaccineDAO.updateVaccine(edited);

        assertEquals(0, vaccineDAO.markSynced(pushed));
        assertFalse(vaccineDAO.findById(vaccine.getId()).isSynced());
    }

    @Test
    void remoteDeletionRemovesOnlyVaccinesNotEditedHere() throws Exception {
        Vaccine synced = TestSupport.newVaccine(animal.getRecordNumber());
        synced.setSynced(true);
        vaccineDAO.insertVaccine(synced);
        Vaccine editedHere = TestSupport.newVaccine(animal.getRecordNumber());
        vaccineDAO.insertVaccine(editedHere);

        vaccineDAO.deleteRemovedRemotely(List.of(synced.getId(), editedHere.getId()));

        assertNull(vaccineDAO.findById(synced.getId()));
        assertNotNull(vaccineDAO.findById(editedHere.getId()), "an unsynced local edit is kept");
        assertTrue(vaccineDAO.getPendingDeletions().isEmpty(),
                "a deletion that happened remotely needs no tombstone");
    }

    @Test
    void deleteVaccineRemovesRow() throws Exception {
        Vaccine vaccine = TestSupport.newVaccine(animal.getRecordNumber());
        vaccineDAO.insertVaccine(vaccine);

        vaccineDAO.deleteVaccine(vaccine.getId());

        assertNull(vaccineDAO.findById(vaccine.getId()));
    }

    @Test
    void getAllUnsyncedVaccinesFiltersBySyncedFlag() throws Exception {
        Vaccine unsynced = TestSupport.newVaccine(animal.getRecordNumber());
        Vaccine synced = TestSupport.newVaccine(animal.getRecordNumber());
        synced.setSynced(true);
        vaccineDAO.insertVaccine(unsynced);
        vaccineDAO.insertVaccine(synced);

        List<Vaccine> result = vaccineDAO.getAllUnsyncedVaccines();
        assertEquals(1, result.size());
        assertEquals(unsynced.getId(), result.get(0).getId());
    }

    /**
     * Regression test for the {@code PRAGMA foreign_keys = ON} fix: hard-deleting an
     * animal must cascade to its vaccines. Without the pragma SQLite ignores the
     * {@code ON DELETE CASCADE} clause and the vaccines would be orphaned.
     */
    @Test
    void deletingAnimalCascadesToVaccines() throws Exception {
        Vaccine vaccine = TestSupport.newVaccine(animal.getRecordNumber());
        vaccineDAO.insertVaccine(vaccine);

        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM animals WHERE record_number = ?")) {
            ps.setString(1, animal.getRecordNumber());
            ps.executeUpdate();
        }

        assertTrue(vaccineDAO.getVaccinesByAnimal(animal.getRecordNumber()).isEmpty(),
                "Vaccines must be removed when their animal is hard-deleted (FK cascade)");
    }
}
