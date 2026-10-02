package com.asosiaciondeasis.animalesdeasis.DAO.Sync;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Sync.RemoteAnimal;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.Sync.RemoteChanges;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.asosiaciondeasis.animalesdeasis.TestSupport;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FirestoreOptions;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * {@link FirestoreRecords} against a real Firestore, in the local emulator.
 *
 * <p>The rest of the suite replaces Firestore with a stand-in, which proves the
 * synchronisation logic and nothing about whether the queries, the server
 * timestamps and the batches behave as that stand-in assumes. This is the check
 * that they do.</p>
 *
 * <p>Skipped unless {@code FIRESTORE_EMULATOR_HOST} is set, which
 * {@code emulators:exec} does for the command it runs. CI runs the whole suite
 * that way; locally, with Node installed:</p>
 * <pre>
 *   npx firebase-tools emulators:exec --only firestore --project demo-animalesdeasis "./mvnw test"
 * </pre>
 * <p>On Windows the command runs under {@code cmd}, so the wrapper is
 * {@code ".\mvnw.cmd test"}.</p>
 */
class FirestoreRecordsEmulatorTest {

    private static final int PLACE_ID = 1;

    private static Firestore db;
    private FirestoreRecords records;

    @BeforeAll
    static void connect() {
        String host = System.getenv("FIRESTORE_EMULATOR_HOST");
        assumeTrue(host != null, "needs the Firestore emulator: see this class's comment");
        db = FirestoreOptions.newBuilder()
                .setProjectId("demo-animalesdeasis")
                .setEmulatorHost(host)
                .build()
                .getService();
    }

    @AfterAll
    static void disconnect() throws Exception {
        if (db != null) {
            db.close();
        }
    }

    @BeforeEach
    void startEmpty() throws Exception {
        db.recursiveDelete(db.collection("animals")).get();
        records = new FirestoreRecords(() -> db);
    }

    private static Animal animal(String name) {
        Animal animal = TestSupport.newAnimal(PLACE_ID);
        animal.setName(name);
        animal.setLastModified("2026-06-01 12:00:00");
        return animal;
    }

    private static List<String> names(RemoteChanges changes) {
        return changes.animals().stream().map(remote -> remote.animal().getName()).sorted().toList();
    }

    @Test
    void aPushedAnimalComesBackWholeWithItsVaccines() throws Exception {
        Animal canela = animal("Canela");
        canela.setChipNumber("900123456789");
        Vaccine rabies = TestSupport.newVaccine(canela.getRecordNumber());

        records.push(List.of(canela), List.of(rabies), Map.of());
        RemoteChanges everything = records.fetchAll();

        assertEquals(1, everything.animals().size());
        RemoteAnimal remote = everything.animals().get(0);
        assertEquals(canela.getRecordNumber(), remote.animal().getRecordNumber());
        assertEquals("Canela", remote.animal().getName());
        assertEquals("900123456789", remote.animal().getChipNumber());
        assertEquals(PLACE_ID, remote.animal().getPlaceId());
        assertEquals("2026-06-01 12:00:00", remote.animal().getLastModified());
        assertEquals(1, remote.vaccines().size());
        assertEquals(rabies.getId(), remote.vaccines().get(0).getId());
        assertEquals("Rabia", remote.vaccines().get(0).getVaccineName());
        assertNotNull(everything.readAt());
    }

    @Test
    void onlyAnimalsWrittenAfterTheGivenReadAreReturned() throws Exception {
        records.push(List.of(animal("Canela")), List.of(), Map.of());
        Instant read = records.fetchAll().readAt();

        records.push(List.of(animal("Luna")), List.of(), Map.of());

        assertEquals(List.of("Luna"), names(records.fetchChangedSince(read)));
    }

    @Test
    void nothingWrittenSinceTheReadMeansNothingReturned() throws Exception {
        records.push(List.of(animal("Canela")), List.of(), Map.of());
        Instant read = records.fetchAll().readAt();

        RemoteChanges changes = records.fetchChangedSince(read);

        assertTrue(changes.animals().isEmpty());
        assertTrue(!changes.readAt().isBefore(read), "an empty answer still says when it was given");
    }

    @Test
    void aNewVaccineMakesItsAnimalComeBackUnharmed() throws Exception {
        Animal canela = animal("Canela");
        records.push(List.of(canela, animal("Luna")), List.of(), Map.of());
        Instant read = records.fetchAll().readAt();

        Vaccine rabies = TestSupport.newVaccine(canela.getRecordNumber());
        records.push(List.of(), List.of(rabies), Map.of());
        RemoteChanges changes = records.fetchChangedSince(read);

        assertEquals(List.of("Canela"), names(changes), "the stamp must not wipe the animal's own fields");
        assertEquals(1, changes.animals().get(0).vaccines().size());
    }

    @Test
    void aDeletedVaccineIsGoneAndItsAnimalComesBack() throws Exception {
        Animal canela = animal("Canela");
        Vaccine rabies = TestSupport.newVaccine(canela.getRecordNumber());
        Vaccine distemper = TestSupport.newVaccine(canela.getRecordNumber());
        records.push(List.of(canela), List.of(rabies, distemper), Map.of());
        Instant read = records.fetchAll().readAt();

        records.push(List.of(), List.of(), Map.of(rabies.getId(), canela.getRecordNumber()));
        RemoteChanges changes = records.fetchChangedSince(read);

        assertEquals(List.of("Canela"), names(changes));
        assertEquals(List.of(distemper.getId()),
                changes.animals().get(0).vaccines().stream().map(Vaccine::getId).toList());
    }

    /** Pushing an animal replaces its document, stamp included, so the stamp has to be put back. */
    @Test
    void anAnimalPushedAgainIsStampedAgain() throws Exception {
        Animal canela = animal("Canela");
        records.push(List.of(canela), List.of(), Map.of());
        Instant read = records.fetchAll().readAt();

        canela.setName("Canela Editada");
        records.push(List.of(canela), List.of(), Map.of());

        assertEquals(List.of("Canela Editada"), names(records.fetchChangedSince(read)));
    }

    /** As written by the Firebase console, or by a version from before stamps existed. */
    @Test
    void anAnimalWithoutAStampIsFoundOnlyByReadingEverything() throws Exception {
        Animal old = animal("Sin sello");
        db.collection("animals").document(old.getRecordNumber()).set(old).get();

        assertTrue(records.fetchChangedSince(Instant.EPOCH).animals().isEmpty());
        assertEquals(List.of("Sin sello"), names(records.fetchAll()));
    }

    /** 300 animals are 600 writes, which Firestore refuses in a single batch. */
    @Test
    void aBacklogLargerThanOneBatchIsUploadedWhole() throws Exception {
        Instant before = records.fetchAll().readAt();
        List<Animal> backlog = new ArrayList<>();
        for (int i = 0; i < 300; i++) {
            backlog.add(animal("Animal " + i));
        }

        records.push(backlog, List.of(), Map.of());

        assertEquals(300, records.fetchAll().animals().size());
        assertEquals(300, records.fetchChangedSince(before).animals().size(), "every one of them stamped");
    }
}
