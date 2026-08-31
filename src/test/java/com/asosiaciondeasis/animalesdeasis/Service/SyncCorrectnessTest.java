package com.asosiaciondeasis.animalesdeasis.Service;

import com.asosiaciondeasis.animalesdeasis.DAO.Animals.AnimalDAO;
import com.asosiaciondeasis.animalesdeasis.DAO.Vaccine.VaccineDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.asosiaciondeasis.animalesdeasis.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two ways synchronisation lost data.
 *
 * <p>Neither of these announced itself. One made a deleted record reappear days
 * later; the other made a large backlog fail to upload entirely, while the
 * exception was logged and swallowed. Both look, to the person using the
 * application, like they imagined it.</p>
 *
 * <p>These tests do not talk to Firestore. What they cover is the local half —
 * the tombstones that make a deletion survive, and the chunk boundaries that
 * decide whether a commit is even legal — which is where both faults lived.</p>
 */
class SyncCorrectnessTest {

    private Connection conn;
    private AnimalDAO animalDAO;
    private VaccineDAO vaccineDAO;
    private int placeId;

    @BeforeEach
    void setUp() throws Exception {
        conn = TestSupport.newInMemoryDatabase();
        placeId = TestSupport.seedPlace(conn);
        animalDAO = new AnimalDAO(conn);
        vaccineDAO = new VaccineDAO(conn);
    }

    @AfterEach
    void tearDown() throws Exception {
        conn.close();
    }

    private Animal newStoredAnimal() throws Exception {
        Animal animal = TestSupport.newAnimal(placeId);
        animalDAO.insertAnimal(animal);
        return animal;
    }

    private Vaccine newStoredVaccine(String animalRecordNumber) throws Exception {
        Vaccine vaccine = TestSupport.newVaccine(animalRecordNumber);
        vaccineDAO.insertVaccine(vaccine);
        return vaccine;
    }

    // -------------------------------------------------------------------------
    //  Deletions made offline
    // -------------------------------------------------------------------------

    /**
     * The regression. Deleting a vaccine used to leave nothing behind, so the
     * next pull found it still in Firebase, saw nothing locally, and put it back.
     */
    @Test
    @DisplayName("deleting a vaccine records that it was deleted")
    void deleteLeavesATombstone() throws Exception {
        Animal animal = newStoredAnimal();
        Vaccine vaccine = newStoredVaccine(animal.getRecordNumber());

        vaccineDAO.deleteVaccine(vaccine.getId());

        assertNull(vaccineDAO.existsVaccine(vaccine.getId()), "the row itself is gone");
        assertEquals(List.of(vaccine.getId()), vaccineDAO.getPendingDeletions(),
                "without this the deletion is invisible to the next sync");
    }

    /**
     * The tombstone has to carry the owning animal, because after the row is
     * gone there is nowhere else to find which remote document to delete.
     */
    @Test
    @DisplayName("the tombstone knows which animal the vaccine belonged to")
    void tombstoneKeepsTheOwningAnimal() throws Exception {
        Animal animal = newStoredAnimal();
        Vaccine vaccine = newStoredVaccine(animal.getRecordNumber());

        vaccineDAO.deleteVaccine(vaccine.getId());

        assertEquals(animal.getRecordNumber(),
                vaccineDAO.getPendingDeletionAnimal(vaccine.getId()));
    }

    @Test
    @DisplayName("a tombstone is cleared only once the deletion has been applied")
    void clearingRemovesThePendingDeletion() throws Exception {
        Animal animal = newStoredAnimal();
        Vaccine vaccine = newStoredVaccine(animal.getRecordNumber());
        vaccineDAO.deleteVaccine(vaccine.getId());

        vaccineDAO.clearPendingDeletion(vaccine.getId());

        assertTrue(vaccineDAO.getPendingDeletions().isEmpty());
        assertNull(vaccineDAO.getPendingDeletionAnimal(vaccine.getId()));
    }

    /**
     * A row removed without its tombstone is precisely the original bug, so the
     * two statements must not be able to come apart.
     */
    @Test
    @DisplayName("deleting a vaccine that does not exist changes nothing")
    void deletingSomethingAbsentLeavesNoTombstone() throws Exception {
        assertThrows(Exception.class, () -> vaccineDAO.deleteVaccine("no-existe"));

        assertTrue(vaccineDAO.getPendingDeletions().isEmpty(),
                "a failed delete must not leave a tombstone for a record that was never there");
    }

    @Test
    @DisplayName("several deletions are all remembered, oldest first")
    void multipleDeletionsAreQueuedInOrder() throws Exception {
        Animal animal = newStoredAnimal();
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            ids.add(newStoredVaccine(animal.getRecordNumber()).getId());
        }

        for (String id : ids) {
            vaccineDAO.deleteVaccine(id);
        }

        assertEquals(5, vaccineDAO.getPendingDeletions().size());
        assertTrue(vaccineDAO.getPendingDeletions().containsAll(ids));
    }

    /**
     * Deleting, then re-creating with the same id, then deleting again must not
     * violate the tombstone table's primary key.
     */
    @Test
    @DisplayName("deleting the same id twice does not fail")
    void repeatedDeletionIsIdempotent() throws Exception {
        Animal animal = newStoredAnimal();
        Vaccine vaccine = newStoredVaccine(animal.getRecordNumber());
        String id = vaccine.getId();

        vaccineDAO.deleteVaccine(id);
        vaccineDAO.insertVaccine(vaccine);
        vaccineDAO.deleteVaccine(id);

        assertEquals(1, vaccineDAO.getPendingDeletions().size());
    }

    @Test
    @DisplayName("a vaccine still present has no tombstone")
    void livingVaccinesAreNotMarked() throws Exception {
        Animal animal = newStoredAnimal();
        newStoredVaccine(animal.getRecordNumber());

        assertTrue(vaccineDAO.getPendingDeletions().isEmpty());
    }

    /** The tombstone table has to survive being created over an existing database. */
    @Test
    @DisplayName("the tombstone table exists in a freshly created schema")
    void schemaCarriesTheTombstoneTable() throws Exception {
        try (PreparedStatement pstmt = conn.prepareStatement(
                "SELECT name FROM sqlite_master WHERE type='table' AND name='deleted_vaccines'");
             ResultSet rs = pstmt.executeQuery()) {
            assertTrue(rs.next(), "deleted_vaccines is missing from the schema");
        }
    }

    // -------------------------------------------------------------------------
    //  Batch limits
    // -------------------------------------------------------------------------

    /**
     * Firestore commits at most 500 operations per batch and fails the whole
     * commit past that, so the more work had piled up offline, the more certain
     * it was that none of it would upload. Off-by-one here is the entire bug.
     */
    @Test
    @DisplayName("writes are split into groups Firestore will accept")
    void partitionRespectsTheLimit() {
        List<Integer> items = IntStream.range(0, 1201).boxed().toList();

        List<List<Integer>> chunks = SyncService.partition(items, 500);

        assertEquals(3, chunks.size());
        assertEquals(500, chunks.get(0).size());
        assertEquals(500, chunks.get(1).size());
        assertEquals(201, chunks.get(2).size());
        assertTrue(chunks.stream().allMatch(c -> c.size() <= 500));
    }

    @Test
    @DisplayName("exactly at the limit stays one group")
    void exactlyAtTheLimitIsNotSplit() {
        List<Integer> items = IntStream.range(0, 500).boxed().toList();

        assertEquals(1, SyncService.partition(items, 500).size());
    }

    @Test
    @DisplayName("one over the limit becomes two groups")
    void oneOverTheLimitSplits() {
        List<Integer> items = IntStream.range(0, 501).boxed().toList();

        List<List<Integer>> chunks = SyncService.partition(items, 500);

        assertEquals(2, chunks.size());
        assertEquals(500, chunks.get(0).size());
        assertEquals(1, chunks.get(1).size());
    }

    @Test
    @DisplayName("nothing to send produces no batches at all")
    void emptyInputProducesNoChunks() {
        assertTrue(SyncService.partition(List.of(), 500).isEmpty(),
                "an empty commit would be a wasted round trip");
    }

    @Test
    @DisplayName("every item survives the split, in order")
    void partitionLosesNothing() {
        List<Integer> items = IntStream.range(0, 1050).boxed().toList();

        List<Integer> flattened = SyncService.partition(items, 500).stream()
                .flatMap(List::stream).toList();

        assertEquals(items, flattened);
    }

    @Test
    @DisplayName("a nonsensical chunk size is rejected rather than looping forever")
    void invalidChunkSizeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> SyncService.partition(List.of(1, 2, 3), 0));
    }

    // -------------------------------------------------------------------------
    //  What the push will pick up
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("a new record is unsynced until it has been sent")
    void newRecordsAreQueuedForUpload() throws Exception {
        Animal animal = newStoredAnimal();
        newStoredVaccine(animal.getRecordNumber());

        assertEquals(1, animalDAO.getUnsyncedAnimals().size());
        assertEquals(1, vaccineDAO.getAllUnsyncedVaccines().size());

        Animal stored = animalDAO.findByRecordNumber(animal.getRecordNumber());
        assertNotNull(stored);
        assertFalse(stored.isSynced());
    }
}
