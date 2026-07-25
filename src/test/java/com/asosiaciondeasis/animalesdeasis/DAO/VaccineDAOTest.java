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

import static org.junit.jupiter.api.Assertions.*;

class VaccineDAOTest {

    private Connection conn;
    private VaccineDAO vaccineDAO;
    private Animal animal;

    @BeforeEach
    void setUp() throws Exception {
        conn = TestSupport.newInMemoryDatabase();
        int placeId = TestSupport.seedPlace(conn);
        AnimalDAO animalDAO = new AnimalDAO(conn);
        animal = TestSupport.newAnimal(placeId);
        animalDAO.insertAnimal(animal);
        vaccineDAO = new VaccineDAO(conn);
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
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
        assertNull(vaccineDAO.existsVaccine("does-not-exist"));
    }

    @Test
    void updateVaccineChangesFields() throws Exception {
        Vaccine vaccine = TestSupport.newVaccine(animal.getRecordNumber());
        vaccineDAO.insertVaccine(vaccine);

        vaccine.setVaccineName("Moquillo");
        vaccineDAO.updateVaccine(vaccine, true);

        assertEquals("Moquillo", vaccineDAO.existsVaccine(vaccine.getId()).getVaccineName());
    }

    @Test
    void deleteVaccineRemovesRow() throws Exception {
        Vaccine vaccine = TestSupport.newVaccine(animal.getRecordNumber());
        vaccineDAO.insertVaccine(vaccine);

        vaccineDAO.deleteVaccine(vaccine.getId());

        assertNull(vaccineDAO.existsVaccine(vaccine.getId()));
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
