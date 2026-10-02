package com.asosiaciondeasis.animalesdeasis.Service;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.Animals.IAnimalDAO;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.RowVersion;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.Sync.IRemoteRecords;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.Sync.ISyncStateDAO;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.Sync.RemoteAnimal;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.Sync.RemoteChanges;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.Sync.SyncState;
import com.asosiaciondeasis.animalesdeasis.Abstraccions.Vaccines.IVaccineDAO;
import com.asosiaciondeasis.animalesdeasis.Config.FirebaseConfig;
import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import com.asosiaciondeasis.animalesdeasis.Model.Vaccine;
import com.asosiaciondeasis.animalesdeasis.Util.NetworkUtils;
import com.asosiaciondeasis.animalesdeasis.Util.SyncEventManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

/**
 * Two-way synchronisation between the local SQLite database and the shared copy.
 *
 * <p>Pull runs before push, and the newer {@code last_modified} wins on either
 * side, so an edit made offline is not overwritten by an older remote copy.
 * Local reads happen once per pass and local writes are applied in batched
 * transactions, each of which re-checks that the row has not been edited since
 * it was read.</p>
 *
 * <p>A pull reads only the animals stamped since the previous one, so its cost
 * follows what changed rather than how many records exist. The first pull, and
 * one every {@link #FULL_PULL_INTERVAL}, reads everything.</p>
 */
public class SyncService {
    private static final Logger log = LoggerFactory.getLogger(SyncService.class);

    private static final DateTimeFormatter DB_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * How far before the previous pull's read an incremental pull starts.
     *
     * <p>A stamp is the time the server received a write, which is slightly
     * before that write becomes visible. A pull resuming exactly where the last
     * one read would skip a write received just before that moment and still
     * landing. Reading those few minutes again costs a handful of reads, once,
     * and applies nothing twice: a record no newer than the local one is
     * ignored.</p>
     */
    static final Duration PULL_OVERLAP = Duration.ofMinutes(5);

    /**
     * Everything is read again this often. Only this application stamps what it
     * writes, so a record edited in the Firebase console, or by an installation
     * that has not been updated, would otherwise never arrive.
     */
    static final Duration FULL_PULL_INTERVAL = Duration.ofDays(30);

    /**
     * One synchronisation at a time, across every instance. The scheduler and the
     * startup sync can overlap, and both read-modify-write the same rows.
     */
    private static final ReentrantLock SYNC_LOCK = new ReentrantLock();

    private final IAnimalDAO animalDAO;
    private final IVaccineDAO vaccineDAO;
    private final ISyncStateDAO syncStateDAO;
    private final IRemoteRecords remote;

    public SyncService(IAnimalDAO animalDAO, IVaccineDAO vaccineDAO, ISyncStateDAO syncStateDAO,
                       IRemoteRecords remote) {
        this.animalDAO = animalDAO;
        this.vaccineDAO = vaccineDAO;
        this.syncStateDAO = syncStateDAO;
        this.remote = remote;
    }

    /**
     * Pulls remote changes, pushes local ones, then notifies listeners. Returns
     * without doing anything when Firebase or the network is unavailable, or when
     * another synchronisation is already running.
     */
    public void sync() {
        if (!FirebaseConfig.isFirebaseAvailable()) {
            log.info("Firebase not available - skipping sync");
            return;
        }
        if (!NetworkUtils.isInternetAvailable()) {
            log.info("No internet connection - skipping sync");
            return;
        }
        try {
            boolean ran = runExclusively(() -> {
                pullChanges(Instant.now());
                pushChanges();
                SyncEventManager.notifyListeners();
            });
            if (!ran) {
                log.info("A sync is already running - skipping this one");
            }
        } catch (Exception e) {
            log.warn("Sync failed; unsynced records stay queued for the next run", e);
        }
    }

    @FunctionalInterface
    interface SyncStep {
        void run() throws Exception;
    }

    /**
     * Runs {@code step} unless another synchronisation holds the lock.
     *
     * @return whether it ran
     */
    static boolean runExclusively(SyncStep step) throws Exception {
        if (!SYNC_LOCK.tryLock()) {
            return false;
        }
        try {
            step.run();
            return true;
        } finally {
            SYNC_LOCK.unlock();
        }
    }

    /**
     * Downloads what changed remotely and applies whatever is new or newer than
     * the local copy. Vaccines deleted remotely are removed locally unless they
     * were edited here since their last sync.
     *
     * <p>How far the pull got is saved only after it has been applied, so a pull
     * that fails halfway is repeated from the same point.</p>
     */
    void pullChanges(Instant now) throws Exception {
        SyncState state = syncStateDAO.load();
        boolean full = needsFullPull(state, now);
        RemoteChanges fetched = full
                ? remote.fetchAll()
                : remote.fetchChangedSince(state.readUpTo().minus(PULL_OVERLAP));

        List<RemoteAnimal> changed = fetched.animals().stream()
                .filter(remoteAnimal -> hasRecordNumber(remoteAnimal.animal()))
                .toList();
        if (!changed.isEmpty()) {
            // Animals first: the vaccines reference them.
            int animals = pullAnimals(changed);
            pullVaccines(changed);
            log.info("{} pull read {} animals from Firebase and applied {}",
                    full ? "Full" : "Incremental", changed.size(), animals);
        }

        syncStateDAO.save(new SyncState(fetched.readAt(), full ? now : state.lastFullPull()));
    }

    private static boolean hasRecordNumber(Animal animal) {
        return animal.getRecordNumber() != null && !animal.getRecordNumber().isBlank();
    }

    /** @return how many animals were new or newer than the local copy */
    private int pullAnimals(List<RemoteAnimal> changed) throws Exception {
        List<Animal> remoteAnimals = new ArrayList<>();
        for (RemoteAnimal remoteAnimal : changed) {
            remoteAnimal.animal().setSynced(true);
            remoteAnimals.add(remoteAnimal.animal());
        }
        Map<String, RowVersion> localAnimals = animalDAO.getRowVersions();
        List<Animal> animalChanges = newerThanLocal(remoteAnimals, Animal::getRecordNumber,
                Animal::getLastModified, localAnimals);
        animalDAO.saveFromRemote(animalChanges, localAnimals);
        return animalChanges.size();
    }

    private void pullVaccines(List<RemoteAnimal> changed) throws Exception {
        // Deletions made here that have not reached Firebase yet. Without this the
        // pull sees the row still present remotely, finds nothing locally, and
        // helpfully puts it back - undoing the deletion the user made offline.
        Set<String> deletedHere = vaccineDAO.getPendingDeletions().keySet();

        Map<String, RowVersion> localVersions = new HashMap<>();
        Map<String, List<Vaccine>> localByAnimal = new HashMap<>();
        for (Vaccine vaccine : vaccineDAO.getAllVaccines()) {
            localVersions.put(vaccine.getId(), new RowVersion(vaccine.getLastModified(), vaccine.isSynced()));
            localByAnimal.computeIfAbsent(vaccine.getAnimalRecordNumber(), k -> new ArrayList<>()).add(vaccine);
        }

        List<Vaccine> remoteVaccines = new ArrayList<>();
        Map<String, RowVersion> removedRemotely = new HashMap<>();
        for (RemoteAnimal remoteAnimal : changed) {
            Set<String> remoteIds = new HashSet<>();
            for (Vaccine vaccine : remoteAnimal.vaccines()) {
                remoteIds.add(vaccine.getId());
                if (deletedHere.contains(vaccine.getId())) {
                    continue;
                }
                vaccine.setSynced(true);
                remoteVaccines.add(vaccine);
            }
            for (Vaccine local : localByAnimal.getOrDefault(remoteAnimal.animal().getRecordNumber(), List.of())) {
                // Only previously synced vaccines: an unsynced one is new here and
                // simply has not been pushed yet.
                if (local.isSynced() && !remoteIds.contains(local.getId())) {
                    removedRemotely.put(local.getId(), localVersions.get(local.getId()));
                }
            }
        }

        List<Vaccine> vaccineChanges = newerThanLocal(remoteVaccines, Vaccine::getId,
                Vaccine::getLastModified, localVersions);
        vaccineDAO.saveFromRemote(vaccineChanges, localVersions);
        vaccineDAO.deleteRemovedRemotely(removedRemotely);

        log.info("Applied {} vaccines from Firebase, removed {} deleted there",
                vaccineChanges.size(), removedRemotely.size());
    }

    /**
     * Whether this pull has to read everything: there is no earlier pull to
     * resume from, or the last full read is older than
     * {@link #FULL_PULL_INTERVAL}.
     */
    static boolean needsFullPull(SyncState state, Instant now) {
        return state.readUpTo() == null
                || state.lastFullPull() == null
                || !now.isBefore(state.lastFullPull().plus(FULL_PULL_INTERVAL));
    }

    /**
     * The remote records worth applying: those missing locally, and those whose
     * timestamp is newer than the local one.
     */
    static <T> List<T> newerThanLocal(List<T> remote, Function<T, String> id,
                                      Function<T, String> lastModified, Map<String, RowVersion> local) {
        List<T> changes = new ArrayList<>();
        for (T record : remote) {
            String key = id.apply(record);
            if (!local.containsKey(key)
                    || isNewer(lastModified.apply(record), local.get(key).lastModified())) {
                changes.add(record);
            }
        }
        return changes;
    }

    /**
     * Whether the remote timestamp is newer. A missing or unreadable timestamp
     * counts as newer, so a malformed record is repaired from the remote copy
     * rather than left diverged forever.
     */
    static boolean isNewer(String remoteTime, String localTime) {
        if (remoteTime == null || localTime == null) {
            return true;
        }
        try {
            return LocalDateTime.parse(remoteTime, DB_FORMATTER)
                    .isAfter(LocalDateTime.parse(localTime, DB_FORMATTER));
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * Uploads local changes and deletions, then marks what was uploaded as
     * synced - only rows still identical to what was sent, so an edit saved
     * during the upload is pushed next time rather than lost.
     */
    void pushChanges() throws Exception {
        List<Animal> unsyncedAnimals = animalDAO.getUnsyncedAnimals();
        List<Vaccine> unsyncedVaccines = vaccineDAO.getAllUnsyncedVaccines();
        Map<String, String> pendingDeletions = vaccineDAO.getPendingDeletions();

        if (unsyncedAnimals.isEmpty() && unsyncedVaccines.isEmpty() && pendingDeletions.isEmpty()) {
            return;
        }

        // Deletions travel with the rest. Applying them in the same pass is what
        // keeps a record deleted offline from coming back on the next pull.
        remote.push(unsyncedAnimals, unsyncedVaccines, pendingDeletions);

        // Marked only after the push that carried them succeeded. Marking first
        // would lose the change permanently if the push then failed.
        int animalsMarked = animalDAO.markSynced(unsyncedAnimals);
        int vaccinesMarked = vaccineDAO.markSynced(unsyncedVaccines);
        vaccineDAO.clearPendingDeletions(pendingDeletions.keySet());

        log.info("Pushed {} animals, {} vaccines, {} deletions ({} animals and {} vaccines were edited "
                        + "during the upload and stay queued)",
                unsyncedAnimals.size(), unsyncedVaccines.size(), pendingDeletions.size(),
                unsyncedAnimals.size() - animalsMarked, unsyncedVaccines.size() - vaccinesMarked);
    }

    /**
     * Deletes a vaccine locally, then tries to delete it remotely.
     *
     * <p>Blocks on the network: call it from a background thread.</p>
     *
     * @throws Exception only when the local deletion fails; a remote failure is
     *         left to the next sync, which the tombstone guarantees
     */
    public void deleteVaccineAndSync(Vaccine vaccine) throws Exception {
        // Delete locally first, which also writes the tombstone. The record then
        // cannot come back regardless of what happens next: if the remote delete
        // fails, or there is no connection at all, the tombstone keeps the
        // deletion pending until a later sync applies it.
        vaccineDAO.deleteVaccine(vaccine.getId());

        if (!FirebaseConfig.isFirebaseAvailable()) {
            return;
        }
        try {
            remote.push(List.of(), List.of(), Map.of(vaccine.getId(), vaccine.getAnimalRecordNumber()));
            vaccineDAO.clearPendingDeletions(List.of(vaccine.getId()));
        } catch (Exception e) {
            // Not rethrown. The deletion has happened as far as the user is
            // concerned, and the tombstone guarantees it reaches Firebase
            // eventually. Failing here would report an error for something that
            // succeeded.
            log.warn("Remote delete of vaccine {} failed; the next sync will retry it",
                    vaccine.getId(), e);
        }
    }
}
