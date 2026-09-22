package com.asosiaciondeasis.animalesdeasis.DAO;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals.DuplicateChipException;
import com.asosiaciondeasis.animalesdeasis.DAO.Animals.AnimalDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnimalDAOTest {

    private TestSupport.TestDatabase db;
    private Connection conn;
    private AnimalDAO dao;
    private int placeId;

    @BeforeEach
    void setUp() throws Exception {
        db = TestSupport.newDatabase();
        conn = db.connection();
        placeId = TestSupport.seedPlace(conn);
        dao = new AnimalDAO(db.dataSource());
    }

    @AfterEach
    void tearDown() throws Exception {
        db.close();
    }

    @Test
    void insertAndFindByRecordNumber() throws Exception {
        Animal animal = TestSupport.newAnimal(placeId);

        dao.insertAnimal(animal);

        Animal found = dao.findByRecordNumber(animal.getRecordNumber());
        assertNotNull(found);
        assertEquals("Firulais", found.getName());
        assertEquals("Perro", found.getSpecies());
    }

    @Test
    void insertWithInvalidPlaceFailsBecauseForeignKeysAreEnforced() {
        Animal animal = TestSupport.newAnimal(9999); // non-existent place

        assertThrows(Exception.class, () -> dao.insertAnimal(animal));
        assertDoesNotThrow(() -> assertNull(dao.findByRecordNumber(animal.getRecordNumber())));
    }

    /**
     * The regression. A failed insert used to be logged and reported as success,
     * so a duplicate chip told the user the animal was saved while nothing was.
     */
    @Test
    void insertWithDuplicateChipIsRejected() throws Exception {
        Animal first = TestSupport.newAnimal(placeId);
        first.setChipNumber("CHIP-1");
        dao.insertAnimal(first);

        Animal second = TestSupport.newAnimal(placeId);
        second.setChipNumber("CHIP-1");

        assertThrows(DuplicateChipException.class, () -> dao.insertAnimal(second));
        assertNull(dao.findByRecordNumber(second.getRecordNumber()));
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
    void localUpdateStampsLastModified() throws Exception {
        Animal animal = TestSupport.newAnimal(placeId);
        animal.setLastModified("2020-01-01 00:00:00");
        dao.insertAnimal(animal);

        animal.setName("Toby");
        dao.updateAnimal(animal);

        Animal stored = dao.findByRecordNumber(animal.getRecordNumber());
        assertEquals("Toby", stored.getName());
        assertNotEquals("2020-01-01 00:00:00", stored.getLastModified());
    }

    // --- Records pulled from Firebase ------------------------------------------

    @Test
    void remoteRecordsAreInsertedAndReplacedInOneCall() throws Exception {
        Animal existing = TestSupport.newAnimal(placeId);
        existing.setLastModified("2020-01-01 00:00:00");
        dao.insertAnimal(existing);

        Animal newer = dao.findByRecordNumber(existing.getRecordNumber());
        newer.setName("Actualizado");
        newer.setLastModified("2021-01-01 00:00:00");
        newer.setSynced(true);
        Animal brandNew = TestSupport.newAnimal(placeId);
        brandNew.setLastModified("2021-01-01 00:00:00");

        dao.saveFromRemote(List.of(newer, brandNew), dao.getRowVersions());

        Animal replaced = dao.findByRecordNumber(existing.getRecordNumber());
        assertEquals("Actualizado", replaced.getName());
        assertEquals("2021-01-01 00:00:00", replaced.getLastModified(), "the remote timestamp is kept");
        assertTrue(replaced.isSynced());
        assertNotNull(dao.findByRecordNumber(brandNew.getRecordNumber()));
    }

    /** A document pulled from Firebase with no timestamp used to break last_modified's NOT NULL. */
    @Test
    void remoteRecordWithoutTimestampIsStampedInsteadOfFailing() throws Exception {
        Animal animal = TestSupport.newAnimal(placeId);
        animal.setLastModified(null);

        dao.saveFromRemote(List.of(animal), Map.of());

        assertNotNull(dao.findByRecordNumber(animal.getRecordNumber()).getLastModified());
    }

    /**
     * The pull compares timestamps, then writes. An edit saved in between used to
     * be overwritten by the remote copy; the write now re-checks the row.
     */
    @Test
    void remoteRecordDoesNotOverwriteAnEditMadeAfterTheComparison() throws Exception {
        Animal animal = TestSupport.newAnimal(placeId);
        animal.setLastModified("2020-01-01 00:00:00");
        dao.insertAnimal(animal);
        var readBeforeTheEdit = dao.getRowVersions();

        animal.setName("Editado aquí");
        dao.updateAnimal(animal);

        Animal remoteCopy = Animal.fromExistingRecord(animal.getRecordNumber());
        remoteCopy.setAdmissionDate(animal.getAdmissionDate());
        remoteCopy.setPlaceId(placeId);
        remoteCopy.setSpecies("Perro");
        remoteCopy.setName("Versión remota");
        remoteCopy.setLastModified("2021-01-01 00:00:00");

        dao.saveFromRemote(List.of(remoteCopy), readBeforeTheEdit);

        assertEquals("Editado aquí", dao.findByRecordNumber(animal.getRecordNumber()).getName());
    }

    /**
     * last_modified has one-second precision, so an edit saved in the same second
     * as the pull's snapshot leaves it unchanged. The edit still flips synced to
     * 0, which is what the guard has to notice.
     */
    @Test
    void remoteRecordDoesNotOverwriteAnEditMadeInTheSameSecondAsTheSnapshot() throws Exception {
        Animal animal = TestSupport.newAnimal(placeId);
        animal.setSynced(true);
        animal.setLastModified("2020-01-01 00:00:00");
        dao.insertAnimal(animal);
        var readBeforeTheEdit = dao.getRowVersions();

        try (var stmt = conn.createStatement()) {
            stmt.executeUpdate("UPDATE animals SET name = 'Editado en el mismo segundo', synced = 0 "
                    + "WHERE record_number = '" + animal.getRecordNumber() + "'");
        }

        Animal remoteCopy = dao.findByRecordNumber(animal.getRecordNumber());
        remoteCopy.setName("Versión remota");
        remoteCopy.setLastModified("2021-01-01 00:00:00");
        remoteCopy.setSynced(true);
        dao.saveFromRemote(List.of(remoteCopy), readBeforeTheEdit);

        assertEquals("Editado en el mismo segundo", dao.findByRecordNumber(animal.getRecordNumber()).getName());
    }

    // --- Marking pushed records -------------------------------------------------

    @Test
    void markSyncedMarksRowsThatAreUnchanged() throws Exception {
        dao.insertAnimal(TestSupport.newAnimal(placeId));
        dao.insertAnimal(TestSupport.newAnimal(placeId));
        List<Animal> pushed = dao.getUnsyncedAnimals();

        assertEquals(2, dao.markSynced(pushed));
        assertTrue(dao.getUnsyncedAnimals().isEmpty());
    }

    /**
     * The regression. Push rewrote each pushed row from the copy it had read, so
     * an edit saved while the upload was in flight was overwritten with the old
     * values and marked synced: gone locally, and never uploaded.
     */
    @Test
    void markSyncedLeavesAnEditMadeDuringTheUploadQueued() throws Exception {
        Animal animal = TestSupport.newAnimal(placeId);
        dao.insertAnimal(animal);
        List<Animal> pushed = dao.getUnsyncedAnimals();

        Animal edited = dao.findByRecordNumber(animal.getRecordNumber());
        edited.setName("Editado durante la subida");
        dao.updateAnimal(edited);

        assertEquals(0, dao.markSynced(pushed));
        Animal stored = dao.findByRecordNumber(animal.getRecordNumber());
        assertEquals("Editado durante la subida", stored.getName());
        assertFalse(stored.isSynced(), "the edit must be pushed next time");
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
        assertThrows(DuplicateChipException.class, () -> dao.updateAnimal(b));
    }
}
