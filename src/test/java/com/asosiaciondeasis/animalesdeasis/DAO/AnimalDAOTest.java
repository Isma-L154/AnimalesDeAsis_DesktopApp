package com.asosiaciondeasis.animalesdeasis.DAO;

import com.asosiaciondeasis.animalesdeasis.DAO.Animals.AnimalDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AnimalDAOTest {

    private Connection conn;
    private AnimalDAO dao;
    private int placeId;

    @BeforeEach
    void setUp() throws Exception {
        conn = TestSupport.newInMemoryDatabase();
        placeId = TestSupport.seedPlace(conn);
        dao = new AnimalDAO(conn);
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    @Test
    void insertAndFindByRecordNumber() throws Exception {
        Animal animal = TestSupport.newAnimal(placeId);

        assertTrue(dao.insertAnimal(animal));

        Animal found = dao.findByRecordNumber(animal.getRecordNumber());
        assertNotNull(found);
        assertEquals("Firulais", found.getName());
        assertEquals("Perro", found.getSpecies());
    }

    @Test
    void insertWithInvalidPlaceFailsBecauseForeignKeysAreEnforced() {
        Animal animal = TestSupport.newAnimal(9999); // non-existent place
        // insertAnimal swallows SQLExceptions and returns false.
        assertDoesNotThrow(() -> assertFalse(dao.insertAnimal(animal)));
    }

    @Test
    void getAllAnimalsReturnsOnlyActive() throws Exception {
        Animal active = TestSupport.newAnimal(placeId);
        Animal inactive = TestSupport.newAnimal(placeId);
        inactive.setActive(false);
        dao.insertAnimal(active);
        dao.insertAnimal(inactive);

        List<Animal> all = dao.getAllAnimals();
        assertEquals(1, all.size());
        assertEquals(active.getRecordNumber(), all.get(0).getRecordNumber());
    }

    @Test
    void softDeleteMarksInactiveAndUnsynced() throws Exception {
        Animal animal = TestSupport.newAnimal(placeId);
        animal.setSynced(true);
        dao.insertAnimal(animal);

        dao.deleteAnimal(animal.getRecordNumber());

        Animal reloaded = dao.findByRecordNumber(animal.getRecordNumber());
        assertFalse(reloaded.isActive());
        assertFalse(reloaded.isSynced(), "A soft delete must flag the record for re-sync");
    }

    @Test
    void reactivateRestoresActiveFlag() throws Exception {
        Animal animal = TestSupport.newAnimal(placeId);
        dao.insertAnimal(animal);
        dao.deleteAnimal(animal.getRecordNumber());

        dao.reactivateAnimal(animal.getRecordNumber());

        assertTrue(dao.findByRecordNumber(animal.getRecordNumber()).isActive());
    }

    @Test
    void getUnsyncedAnimalsReturnsOnlyUnsynced() throws Exception {
        Animal unsynced = TestSupport.newAnimal(placeId);
        Animal synced = TestSupport.newAnimal(placeId);
        synced.setSynced(true);
        dao.insertAnimal(unsynced);
        dao.insertAnimal(synced);

        List<Animal> result = dao.getUnsyncedAnimals();
        assertEquals(1, result.size());
        assertEquals(unsynced.getRecordNumber(), result.get(0).getRecordNumber());
    }

    @Test
    void findByFiltersMatchesSpeciesAndDateRange() throws Exception {
        Animal dog = TestSupport.newAnimal(placeId);
        dog.setSpecies("Perro");
        dog.setAdmissionDate("2024-05-10");
        Animal cat = TestSupport.newAnimal(placeId);
        cat.setSpecies("Gato");
        cat.setAdmissionDate("2024-05-10");
        dao.insertAnimal(dog);
        dao.insertAnimal(cat);

        List<Animal> dogs = dao.findByFilters("Perro", "2024-01-01", "2024-12-31", null, false);
        assertEquals(1, dogs.size());
        assertEquals("Perro", dogs.get(0).getSpecies());

        List<Animal> none = dao.findByFilters("Perro", "2023-01-01", "2023-12-31", null, false);
        assertTrue(none.isEmpty());
    }

    @Test
    void chipNumberUniquenessIsEnforcedOnUpdate() throws Exception {
        Animal a = TestSupport.newAnimal(placeId);
        a.setChipNumber("CHIP-1");
        Animal b = TestSupport.newAnimal(placeId);
        b.setChipNumber("CHIP-2");
        dao.insertAnimal(a);
        dao.insertAnimal(b);

        b.setChipNumber("CHIP-1"); // collide with a
        Exception ex = assertThrows(Exception.class, () -> dao.updateAnimal(b, true));
        assertTrue(ex.getMessage().toLowerCase().contains("unique"));
    }
}
