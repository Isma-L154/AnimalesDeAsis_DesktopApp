package com.asosiaciondeasis.animalesdeasis.DAO.Sync;

import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The parts of a push that are decided before Firestore is involved: how writes
 * are grouped, and which animals get stamped.
 *
 * <p>What Firestore itself does with them is covered by
 * {@link FirestoreRecordsEmulatorTest}.</p>
 */
class FirestoreRecordsTest {

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

        List<List<Integer>> chunks = FirestoreRecords.partition(items, 500);

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

        assertEquals(1, FirestoreRecords.partition(items, 500).size());
    }

    @Test
    @DisplayName("one over the limit becomes two groups")
    void oneOverTheLimitSplits() {
        List<Integer> items = IntStream.range(0, 501).boxed().toList();

        List<List<Integer>> chunks = FirestoreRecords.partition(items, 500);

        assertEquals(2, chunks.size());
        assertEquals(500, chunks.get(0).size());
        assertEquals(1, chunks.get(1).size());
    }

    @Test
    @DisplayName("nothing to send produces no batches at all")
    void emptyInputProducesNoChunks() {
        assertTrue(FirestoreRecords.partition(List.of(), 500).isEmpty(),
                "an empty commit would be a wasted round trip");
    }

    @Test
    @DisplayName("every item survives the split, in order")
    void partitionLosesNothing() {
        List<Integer> items = IntStream.range(0, 1050).boxed().toList();

        List<Integer> flattened = FirestoreRecords.partition(items, 500).stream()
                .flatMap(List::stream).toList();

        assertEquals(items, flattened);
    }

    @Test
    @DisplayName("a nonsensical chunk size is rejected rather than looping forever")
    void invalidChunkSizeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> FirestoreRecords.partition(List.of(1, 2, 3), 0));
    }

    // -------------------------------------------------------------------------
    //  Stamps
    // -------------------------------------------------------------------------

    /**
     * Vaccines carry no stamp of their own. If the owner of a changed or
     * deleted vaccine were not stamped, no other installation would ever look
     * at that animal again, and the change would stay where it was made.
     */
    @Test
    @DisplayName("a changed or deleted vaccine stamps the animal it belongs to")
    void vaccineChangesStampTheirAnimal() {
        Animal pushed = Animal.fromExistingRecord("pushed");
        Vaccine changed = Vaccine.createNew();
        changed.setAnimalRecordNumber("owner-of-changed");

        assertEquals(List.of("pushed", "owner-of-changed", "owner-of-deleted"),
                new ArrayList<>(FirestoreRecords.stampedAnimals(
                        List.of(pushed), List.of(changed), Map.of("deleted-vaccine", "owner-of-deleted"))));
    }

    @Test
    @DisplayName("an animal is stamped once, however many of its records changed")
    void eachAnimalIsStampedOnce() {
        Animal animal = Animal.fromExistingRecord("canela");
        Vaccine first = Vaccine.createNew();
        first.setAnimalRecordNumber("canela");
        Vaccine second = Vaccine.createNew();
        second.setAnimalRecordNumber("canela");

        assertEquals(1, FirestoreRecords.stampedAnimals(
                List.of(animal), List.of(first, second), Map.of("deleted-vaccine", "canela")).size());
    }
}
