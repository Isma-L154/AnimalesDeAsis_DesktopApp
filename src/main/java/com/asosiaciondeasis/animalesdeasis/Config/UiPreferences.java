package com.asosiaciondeasis.animalesdeasis.Config;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/**
 * Small interface preferences that should survive restarting the application:
 * whether the navigation rail is collapsed, and which section was last open.
 *
 * <p>Backed by {@link Preferences}, which puts these in the per-user store the
 * operating system already provides (the registry on Windows, a plist on macOS,
 * a dotfile on Linux). Deliberately not the SQLite database: this is one
 * person's window layout on one machine, it has nothing to do with the
 * association's records, and it must never end up in a sync.</p>
 *
 * <p>Every read has a fallback and every write swallows its failure. Preferences
 * can be unavailable — a locked-down profile, a read-only registry hive — and a
 * shelter's animal records must not become unreachable because a remembered
 * sidebar width could not be saved.</p>
 */
public final class UiPreferences {

    private static final String KEY_RAIL_COLLAPSED = "railCollapsed";
    private static final String KEY_LAST_SECTION = "lastSection";

    private static final Preferences PREFS =
            Preferences.userNodeForPackage(UiPreferences.class);

    private UiPreferences() {
        // Utility class.
    }

    public static boolean isRailCollapsed() {
        try {
            return PREFS.getBoolean(KEY_RAIL_COLLAPSED, false);
        } catch (RuntimeException e) {
            return false;
        }
    }

    public static void setRailCollapsed(boolean collapsed) {
        try {
            PREFS.putBoolean(KEY_RAIL_COLLAPSED, collapsed);
        } catch (RuntimeException e) {
            // A preference that cannot be stored is not worth an error path.
        }
    }

    /**
     * @param fallback returned when nothing has been stored yet, or the store is
     *                 unreadable
     */
    public static String getLastSection(String fallback) {
        try {
            String stored = PREFS.get(KEY_LAST_SECTION, null);
            return (stored == null || stored.isBlank()) ? fallback : stored;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    public static void setLastSection(String sectionId) {
        try {
            if (sectionId != null && !sectionId.isBlank()) {
                PREFS.put(KEY_LAST_SECTION, sectionId);
            }
        } catch (RuntimeException e) {
            // See above.
        }
    }

    /** Clears everything stored here. Exists for tests, which must not inherit developer state. */
    public static void clear() {
        try {
            PREFS.clear();
        } catch (BackingStoreException | RuntimeException e) {
            // Nothing useful to do.
        }
    }
}
