package com.asosiaciondeasis.animalesdeasis.Util;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Application-wide registry of callbacks to run when a synchronisation finishes.
 *
 * <p><b>Why the list is copy-on-write.</b> The two sides of this class run on
 * different threads and always have. {@link #notifyListeners()} is called from
 * {@code SyncService} on a background thread, while {@link #addListener} and
 * {@link #removeListener} are called from controllers on the JavaFX application
 * thread as screens are opened and closed. A plain {@code ArrayList} under that
 * pattern is a race: a navigation that happens while a sync is completing can
 * throw {@code ConcurrentModificationException} mid-iteration, leaving the
 * remaining listeners unnotified and the screens they belong to showing stale
 * data. It is intermittent and it fails silently, which is the worst
 * combination.</p>
 *
 * <p>{@link CopyOnWriteArrayList} makes iteration safe against concurrent
 * modification without any locking on the notify path. Writes copy the whole
 * array, which is the right trade here: listeners are added and removed once per
 * navigation, and there are never more than a handful.</p>
 */
public final class SyncEventManager {

    private static final List<Runnable> listeners = new CopyOnWriteArrayList<>();

    private SyncEventManager() {
        // Utility class.
    }

    /**
     * Registers a callback to run after each completed synchronisation.
     *
     * <p>Whoever adds a listener is responsible for removing it — see
     * {@code IPortalAwareController.cleanup()}. The list is static, so a listener
     * that is never removed keeps its controller, its scene graph and everything
     * they reference alive for the lifetime of the application.</p>
     */
    public static void addListener(Runnable listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /** Removes a previously registered listener. Safe to call with one never added. */
    public static void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    /**
     * Runs every registered listener.
     *
     * <p>A listener that throws is logged and skipped rather than allowed to
     * abort the loop, so one broken screen cannot stop the others from being
     * told that data changed.</p>
     *
     * <p>Called from a background thread. Listeners that touch the interface must
     * hop to the JavaFX application thread themselves.</p>
     */
    public static void notifyListeners() {
        for (Runnable listener : listeners) {
            try {
                listener.run();
            } catch (Exception e) {
                System.out.println("Error notifying sync listener: " + e.getMessage());
            }
        }
    }

    /** Number of registered listeners. Exists so tests can prove cleanup happened. */
    public static int listenerCount() {
        return listeners.size();
    }
}
