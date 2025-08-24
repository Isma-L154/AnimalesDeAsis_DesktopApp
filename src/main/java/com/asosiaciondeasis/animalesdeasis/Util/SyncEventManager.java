package com.asosiaciondeasis.animalesdeasis.Util;

import java.util.ArrayList;
import java.util.List;

/**
 * SyncEventManager is a utility class for managing synchronization event listeners.
 * It allows components to register, remove, and notify listeners when a synchronization event occurs.
 * Listeners are represented as Runnable instances and are executed when notified.
 */
public class SyncEventManager {
    private static final List<Runnable> listeners = new ArrayList<>();

    /**
     * Registers a new listener to be notified on synchronization events.
     *
     * @param listener The Runnable to be executed when a sync event occurs.
     */
    public static void addListener(Runnable listener) {
        listeners.add(listener);
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener The Runnable to be removed from the notification list.
     */
    public static void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

    /**
     * Notifies all registered listeners by executing their run() method.
     * If a listener throws an exception, it is caught and logged, allowing other listeners to be notified.
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
}
