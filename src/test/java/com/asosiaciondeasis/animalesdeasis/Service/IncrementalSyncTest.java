package com.asosiaciondeasis.animalesdeasis.Service;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Sync.SyncState;
import com.asosiaciondeasis.animalesdeasis.DAO.Animals.AnimalDAO;
import com.asosiaciondeasis.animalesdeasis.DAO.Sync.SyncStateDAO;
import com.asosiaciondeasis.animalesdeasis.DAO.Vaccine.VaccineDAO;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.asosiaciondeasis.animalesdeasis.TestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.PreparedStatement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Two installations, each with its own SQLite database, synchronising through
 * one shared copy.
 *
 * <p>Everything here runs the real {@link SyncService} and the real DAOs; only
 * Firestore is replaced. What is being checked is that reading just the changes
 * loses nothing that reading everything would have found.</p>
 */
class IncrementalSyncTest {

    private static final Instant MORNING = Instant.parse("2026-06-01T12:00:00Z");
    /** Longer than the overlap, so a pull after it re-reads nothing by accident. */
    private static final Duration A_WHILE = Duration.ofMinutes(30);

    /** One machine: its database, its DAOs and its synchronisation. */
    private static final class Installation implements AutoCloseable {
        final TestSupport.TestDatabase db;
        final AnimalDAO animals;
        final VaccineDAO vaccines;
        final SyncStateDAO syncState;
        final SyncService sync;
        final int placeId;

        Installation(InMemoryRemoteRecords remote) throws Exception {
            db = TestSupport.newDatabase();
            placeId = TestSupport.seedPlace(db.connection());
            animals = new AnimalDAO(db.dataSource());
            vaccines = new VaccineDAO(db.dataSource());
            syncState = new SyncStateDAO(db.dataSource());
            sync = new SyncService(animals, vaccines, syncState, remote);
        }

        Animal register(String name) throws Exception {
            Animal animal = TestSupport.newAnimal(placeId);
            animal.setName(name);
            animals.insertAnimal(animal);
            return animal;
        }

        Vaccine vaccinate(Animal animal) throws Exception {
            Vaccine vaccine = TestSupport.newVaccine(animal.getRecordNumber());
            vaccines.insertVaccine(vaccine);
            return vaccine;
        }

        /**
         * Renames an animal the way the edit form does. {@code last_modified} has
         * one-second precision and a test runs inside one second, so the time
         * of the edit is given rather than left to the clock.
         */
        void rename(String recordNumber, String name, String editedAt) throws Exception {
            Animal animal = animals.findByRecordNumber(recordNumber);
            animal.setName(name);
            animal.setSynced(false);
            animals.updateAnimal(animal);
            try (PreparedStatement pstmt = db.connection().prepareStatement(
                    "UPDATE animals SET last_modified = ? WHERE record_number = ?")) {
                pstmt.setString(1, editedAt);
                pstmt.setString(2, recordNumber);
                pstmt.executeUpdate();
            }
        }

        String nameOf(String recordNumber) throws Exception {
            return animals.findByRecordNumber(recordNumber).getName();
        }

        @Override
        public void close() throws Exception {
            db.close();
        }
    }

    private InMemoryRemoteRecords remote;
    private Installation office;
    private Installation clinic;

    @BeforeEach
    void setUp() throws Exception {
        remote = new InMemoryRemoteRecords();
        office = new Installation(remote);
        clinic = new Installation(remote);
    }

    @AfterEach
    void tearDown() throws Exception {
        office.close();
        clinic.close();
    }

    /**
     * Both installations hold the same records, have nothing left to send, and
     * pulled long enough after the last write that none of it is read again.
     */
    private void bothInSync() throws Exception {
        office.sync.pushChanges();
        remote.tick(A_WHILE);
        clinic.sync.pullChanges(MORNING);
        office.sync.pullChanges(MORNING);
        remote.tick(A_WHILE);
        remote.fetchSizes.clear();
    }

    @Test
    void theFirstPullReadsEverythingAndRemembersWhereItGotTo() throws Exception {
        Animal canela = office.register("Canela");
        Vaccine rabies = office.vaccinate(canela);
        office.sync.pushChanges();

        clinic.sync.pullChanges(MORNING);

        assertEquals("Canela", clinic.nameOf(canela.getRecordNumber()));
        assertNotNull(clinic.vaccines.findById(rabies.getId()));
        assertEquals(1, remote.fullFetches);
        assertEquals(new SyncState(remote.now(), MORNING), clinic.syncState.load());
    }

    @Test
    void aLaterPullReadsOnlyWhatChanged() throws Exception {
        office.register("Canela");
        Animal luna = office.register("Luna");
        office.register("Toby");
        bothInSync();

        office.rename(luna.getRecordNumber(), "Luna Nueva", "2030-01-01 00:00:00");
        office.sync.pushChanges();
        clinic.sync.pullChanges(MORNING.plus(Duration.ofHours(1)));

        assertEquals("Luna Nueva", clinic.nameOf(luna.getRecordNumber()));
        assertEquals(List.of(1), remote.fetchSizes, "one changed animal, so one animal read out of three");
    }

    @Test
    void nothingChangedMeansNothingIsRead() throws Exception {
        office.register("Canela");
        office.register("Luna");
        bothInSync();

        clinic.sync.pullChanges(MORNING.plus(Duration.ofHours(1)));

        assertEquals(List.of(0), remote.fetchSizes);
    }

    @Test
    void aNewVaccineArrivesThroughItsAnimalsStamp() throws Exception {
        Animal canela = office.register("Canela");
        office.register("Luna");
        bothInSync();

        Vaccine rabies = office.vaccinate(canela);
        office.sync.pushChanges();
        clinic.sync.pullChanges(MORNING.plus(Duration.ofHours(1)));

        assertNotNull(clinic.vaccines.findById(rabies.getId()),
                "the vaccine has no stamp of its own; its animal's is what makes it visible");
        assertEquals(List.of(1), remote.fetchSizes);
    }

    @Test
    void aVaccineDeletedElsewhereIsRemovedByAnIncrementalPull() throws Exception {
        Animal canela = office.register("Canela");
        Vaccine rabies = office.vaccinate(canela);
        bothInSync();
        assertNotNull(clinic.vaccines.findById(rabies.getId()));

        int fullFetches = remote.fullFetches;
        office.vaccines.deleteVaccine(rabies.getId());
        office.sync.pushChanges();
        clinic.sync.pullChanges(MORNING.plus(Duration.ofHours(1)));

        assertEquals(fullFetches, remote.fullFetches, "found without reading everything again");
        assertNull(clinic.vaccines.findById(rabies.getId()));
        assertTrue(office.vaccines.getPendingDeletions().isEmpty(), "the deletion reached the shared copy");
    }

    @Test
    void aVaccineDeletedHereIsNotBroughtBackByThePull() throws Exception {
        Animal canela = office.register("Canela");
        Vaccine rabies = office.vaccinate(canela);
        bothInSync();

        clinic.vaccines.deleteVaccine(rabies.getId());
        // The animal changes elsewhere, so the next pull reads it again with the
        // vaccine still attached remotely.
        office.rename(canela.getRecordNumber(), "Canela Editada", "2030-01-01 00:00:00");
        office.sync.pushChanges();
        clinic.sync.pullChanges(MORNING.plus(Duration.ofHours(1)));

        assertNull(clinic.vaccines.findById(rabies.getId()), "the pull must not undo a deletion made offline");

        clinic.sync.pushChanges();
        remote.tick(A_WHILE);
        office.sync.pullChanges(MORNING.plus(Duration.ofHours(2)));

        assertNull(office.vaccines.findById(rabies.getId()), "and the deletion reaches the other installation");
    }

    @Test
    void anEditMadeHereSurvivesAnOlderRemoteCopy() throws Exception {
        Animal canela = office.register("Canela");
        bothInSync();

        office.rename(canela.getRecordNumber(), "Edición vieja", "2030-01-01 00:00:00");
        office.sync.pushChanges();
        clinic.rename(canela.getRecordNumber(), "Edición nueva", "2030-01-02 00:00:00");
        clinic.sync.pullChanges(MORNING.plus(Duration.ofHours(1)));

        assertEquals("Edición nueva", clinic.nameOf(canela.getRecordNumber()));

        clinic.sync.pushChanges();
        remote.tick(A_WHILE);
        office.sync.pullChanges(MORNING.plus(Duration.ofHours(2)));

        assertEquals("Edición nueva", office.nameOf(canela.getRecordNumber()));
    }

    /**
     * A stamp is when the server received a write, not when it became visible.
     * A pull that resumed exactly from where the last one read would skip this
     * record for good.
     */
    @Test
    void aWriteThatLandsLateIsStillPickedUp() throws Exception {
        office.register("Canela");
        bothInSync();
        Instant lastRead = clinic.syncState.load().readUpTo();

        Animal late = TestSupport.newAnimal(clinic.placeId);
        late.setName("Tardío");
        late.setLastModified("2026-01-01 00:00:00");
        remote.landLate(late, lastRead.minus(Duration.ofMinutes(1)));
        clinic.sync.pullChanges(MORNING.plus(Duration.ofHours(1)));

        assertEquals("Tardío", clinic.nameOf(late.getRecordNumber()));
    }

    /**
     * The price of the overlap, and its limit: what was written just before a
     * pull is read once more by the next one, and then left alone. Without the
     * limit, a large upload would be read again by every pull that followed.
     */
    @Test
    void recordsWrittenJustBeforeAPullAreReadOnceMoreAndThenNotAgain() throws Exception {
        office.register("Canela");
        office.register("Luna");
        office.sync.pushChanges();
        clinic.sync.pullChanges(MORNING);
        remote.fetchSizes.clear();

        remote.tick(A_WHILE);
        clinic.sync.pullChanges(MORNING.plus(Duration.ofHours(1)));
        remote.tick(A_WHILE);
        clinic.sync.pullChanges(MORNING.plus(Duration.ofHours(2)));

        assertEquals(List.of(2, 0), remote.fetchSizes);
    }

    /** An edit made in the Firebase console, or by a version that does not stamp. */
    @Test
    void aChangeWithoutAStampArrivesWithTheNextFullPull() throws Exception {
        Animal canela = office.register("Canela");
        bothInSync();

        Animal edited = office.animals.findByRecordNumber(canela.getRecordNumber());
        edited.setName("Editada en la consola");
        edited.setLastModified("2030-01-01 00:00:00");
        remote.editWithoutStamp(edited);

        clinic.sync.pullChanges(MORNING.plus(Duration.ofDays(1)));
        assertEquals("Canela", clinic.nameOf(canela.getRecordNumber()), "invisible to an incremental pull");

        clinic.sync.pullChanges(MORNING.plus(SyncService.FULL_PULL_INTERVAL));
        assertEquals("Editada en la consola", clinic.nameOf(canela.getRecordNumber()));
        assertEquals(MORNING.plus(SyncService.FULL_PULL_INTERVAL), clinic.syncState.load().lastFullPull());
    }

    @Test
    void aPullThatCannotBeAppliedIsRepeatedFromTheSamePoint() throws Exception {
        office.register("Canela");
        bothInSync();
        SyncState before = clinic.syncState.load();

        // A place the clinic's database does not have, so applying it fails.
        Animal elsewhere = office.register("De otro lugar");
        elsewhere.setPlaceId(9999);
        remote.push(List.of(elsewhere), List.of(), Map.of());

        assertThrows(Exception.class, () -> clinic.sync.pullChanges(MORNING.plus(Duration.ofHours(1))));
        assertEquals(before, clinic.syncState.load(),
                "moving on would mean never reading that record again");
    }

    @Test
    void aFailedPushLeavesEverythingQueuedForTheNextOne() throws Exception {
        Animal canela = office.register("Canela");
        Vaccine rabies = office.vaccinate(canela);
        Vaccine doomed = office.vaccinate(canela);
        office.sync.pushChanges();
        office.vaccines.deleteVaccine(doomed.getId());
        office.rename(canela.getRecordNumber(), "Canela Editada", "2030-01-01 00:00:00");

        remote.failNextPush = true;
        assertThrows(IllegalStateException.class, () -> office.sync.pushChanges());

        assertEquals(1, office.animals.getUnsyncedAnimals().size());
        assertFalse(office.vaccines.getPendingDeletions().isEmpty());

        office.sync.pushChanges();
        clinic.sync.pullChanges(MORNING);

        assertEquals("Canela Editada", clinic.nameOf(canela.getRecordNumber()));
        assertNotNull(clinic.vaccines.findById(rabies.getId()));
        assertNull(clinic.vaccines.findById(doomed.getId()));
        assertTrue(office.animals.getUnsyncedAnimals().isEmpty());
        assertTrue(office.vaccines.getPendingDeletions().isEmpty());
    }
}
