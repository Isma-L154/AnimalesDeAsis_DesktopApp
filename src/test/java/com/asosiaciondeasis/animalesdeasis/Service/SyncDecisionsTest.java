package com.asosiaciondeasis.animalesdeasis.Service;

import com.asosiaciondeasis.animalesdeasis.Model.Animal;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The parts of synchronisation that decide what happens, tested without Firestore. */
class SyncDecisionsTest {

    private static Animal remote(String recordNumber, String lastModified) {
        Animal animal = Animal.fromExistingRecord(recordNumber);
        animal.setLastModified(lastModified);
        return animal;
    }

    @Test
    void appliesRecordsMissingLocallyOrNewerThanTheLocalCopy() {
        List<Animal> remote = List.of(
                remote("new", "2024-01-01 00:00:00"),
                remote("newer", "2024-06-01 00:00:00"),
                remote("older", "2024-01-01 00:00:00"),
                remote("same", "2024-03-01 10:00:00"));
        Map<String, String> local = Map.of(
                "newer", "2024-05-01 00:00:00",
                "older", "2024-02-01 00:00:00",
                "same", "2024-03-01 10:00:00");

        List<Animal> changes = SyncService.newerThanLocal(remote, Animal::getRecordNumber,
                Animal::getLastModified, local);

        assertEquals(List.of("new", "newer"), changes.stream().map(Animal::getRecordNumber).toList());
    }

    @Test
    void aMissingOrUnreadableTimestampLetsTheRemoteCopyWin() {
        assertTrue(SyncService.isNewer(null, "2024-01-01 00:00:00"));
        assertTrue(SyncService.isNewer("2024-01-01 00:00:00", null));
        assertTrue(SyncService.isNewer("not a date", "2024-01-01 00:00:00"));
        assertFalse(SyncService.isNewer("2024-01-01 00:00:00", "2024-01-01 00:00:01"));
    }

    @Test
    void aSecondSyncIsSkippedWhileOneIsRunning() throws Exception {
        CountDownLatch firstEntered = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        CompletableFuture<Boolean> first = CompletableFuture.supplyAsync(() -> {
            try {
                return SyncService.runExclusively(() -> {
                    firstEntered.countDown();
                    releaseFirst.await();
                });
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        assertTrue(firstEntered.await(5, TimeUnit.SECONDS));

        assertFalse(SyncService.runExclusively(() -> { }), "the overlapping sync must not run");

        releaseFirst.countDown();
        assertTrue(first.get(5, TimeUnit.SECONDS));
        assertTrue(SyncService.runExclusively(() -> { }), "the lock is released afterwards");
    }

    @Test
    void theLockIsReleasedWhenASyncFails() throws Exception {
        try {
            SyncService.runExclusively(() -> {
                throw new IllegalStateException("network dropped");
            });
        } catch (IllegalStateException expected) {
            // The failure itself is the caller's to log.
        }

        assertTrue(SyncService.runExclusively(() -> { }));
    }
}
