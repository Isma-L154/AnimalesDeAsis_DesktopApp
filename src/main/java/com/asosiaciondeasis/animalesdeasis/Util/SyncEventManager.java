package com.asosiaciondeasis.animalesdeasis.Util;

import java.util.ArrayList;
import java.util.List;

public class SyncEventManager {
    private static final List<Runnable> listeners = new ArrayList<>();

    public static void addListener(Runnable listener) {
        listeners.add(listener);
    }

    public static void removeListener(Runnable listener) {
        listeners.remove(listener);
    }

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
