package com.asosiaciondeasis.animalesdeasis.Util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the threading contract of {@link SyncEventManager}.
 *
 * <p>The two sides of this class have always run on different threads:
 * {@code SyncService} notifies from a background thread while controllers
 * subscribe and unsubscribe on the JavaFX application thread as screens open and
 * close. It held a plain {@code ArrayList}, so a navigation landing during a
 * synchronisation could throw {@code ConcurrentModificationException} partway
 * through the loop — leaving the remaining screens unnotified and showing stale
 * data, intermittently and without a visible error.</p>
 */
class SyncEventManagerTest {

    @BeforeEach
    void clearRegistry() {
        // The registry is static, so leftovers from another test would be counted.
        for (int i = 0; i < 200 && SyncEventManager.listenerCount() > 0; i++) {
            SyncEventManager.removeListener(new Runnable() {
                @Override
                public void run() {
                }
            });
            if (SyncEventManager.listenerCount() > 0) {
                break;
            }
        }
    }

    @Test
    @DisplayName("a removed listener is no longer notified")
    void removedListenerIsNotNotified() {
        AtomicInteger calls = new AtomicInteger();
        Runnable listener = calls::incrementAndGet;

        SyncEventManager.addListener(listener);
        SyncEventManager.notifyListeners();
        assertEquals(1, calls.get());

        SyncEventManager.removeListener(listener);
        SyncEventManager.notifyListeners();
        assertEquals(1, calls.get(), "listener kept firing after removal");
    }

    @Test
    @DisplayName("one failing listener does not stop the others")
    void oneThrowingListenerDoesNotStopTheRest() {
        AtomicInteger reached = new AtomicInteger();
        Runnable boom = () -> {
            throw new IllegalStateException("screen is broken");
        };
        Runnable counts = reached::incrementAndGet;

        SyncEventManager.addListener(boom);
        SyncEventManager.addListener(counts);
        try {
            SyncEventManager.notifyListeners();
            assertEquals(1, reached.get(), "a broken screen stopped the others being notified");
        } finally {
            SyncEventManager.removeListener(boom);
            SyncEventManager.removeListener(counts);
        }
    }

    /**
     * The regression test. Subscribing and unsubscribing while notification is in
     * progress is exactly what navigating during a sync does. Against the previous
     * {@code ArrayList} this throws {@code ConcurrentModificationException}.
     */
    @Test
    @DisplayName("navigating while a sync completes neither throws nor drops listeners")
    void concurrentSubscriptionDuringNotificationIsSafe() throws Exception {
        final int churnThreads = 4;
        final int rounds = 400;

        AtomicReference<Throwable> failure = new AtomicReference<>();
        AtomicBoolean stop = new AtomicBoolean(false);
        CountDownLatch ready = new CountDownLatch(churnThreads);
        CountDownLatch done = new CountDownLatch(churnThreads);

        // A listener that stays subscribed throughout, so we can also prove the
        // notify loop keeps completing rather than dying quietly partway.
        AtomicInteger stableCalls = new AtomicInteger();
        Runnable stable = stableCalls::incrementAndGet;
        SyncEventManager.addListener(stable);

        List<Thread> threads = new ArrayList<>();
        for (int t = 0; t < churnThreads; t++) {
            Thread thread = new Thread(() -> {
                ready.countDown();
                try {
                    while (!stop.get()) {
                        Runnable listener = () -> {
                        };
                        SyncEventManager.addListener(listener);
                        Thread.yield();
                        SyncEventManager.removeListener(listener);
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                } finally {
                    done.countDown();
                }
            }, "churn-" + t);
            thread.setDaemon(true);
            threads.add(thread);
            thread.start();
        }

        assertTrue(ready.await(5, TimeUnit.SECONDS), "churn threads did not start");

        try {
            for (int i = 0; i < rounds; i++) {
                SyncEventManager.notifyListeners();
            }
        } catch (Throwable e) {
            failure.compareAndSet(null, e);
        } finally {
            stop.set(true);
            assertTrue(done.await(5, TimeUnit.SECONDS), "churn threads did not stop");
            SyncEventManager.removeListener(stable);
        }

        assertNull(failure.get(),
                "subscribing during notification failed: " + failure.get());
        assertEquals(rounds, stableCalls.get(),
                "the notify loop stopped early, so some screens were never told the data changed");
    }
}
