package com.asosiaciondeasis.animalesdeasis.Config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;

/**
 * Brings up the Firebase Admin SDK, or explains why it could not.
 *
 * <p>Failure here is never fatal: the application is offline-first and works
 * entirely against local SQLite. What changed is that it no longer fails
 * <em>quietly</em>. Every problem used to end at {@code printStackTrace()}
 * followed by offline mode, so a shelter could run for months believing records
 * were reaching the cloud while the passphrase had been wrong since the day it
 * was installed.</p>
 */
public final class FirebaseConfig {
    private static final Logger log = LoggerFactory.getLogger(FirebaseConfig.class);

    private static boolean initialized;
    /** Read by the sync-status poller and the sync thread, written once at startup. */
    private static volatile boolean firebaseAvailable;

    private FirebaseConfig() {
    }

    public static synchronized boolean initialize() {
        if (initialized) {
            return firebaseAvailable;
        }
        initialized = true;

        try (InputStream serviceAccount = CredentialsManager.getDecryptedCredentials()) {
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            FirebaseApp.initializeApp(options);
            firebaseAvailable = true;
            log.info("Firebase initialised: synchronisation is available");
            return true;

        } catch (CredentialsException e) {
            report(e.reason(), e.getMessage());
            return false;

        } catch (Exception e) {
            log.warn("Could not initialise Firebase; synchronisation is disabled", e);
            return false;
        }
    }

    /**
     * A missing bundle is a configuration, not a fault; the rest are faults and
     * are worth saying loudly, because the symptom is identical in every case —
     * synchronisation simply never happens.
     */
    private static void report(CredentialsException.Reason reason, String message) {
        if (reason == CredentialsException.Reason.MISSING_BUNDLE) {
            // Not a fault: an installation with no credentials is a supported
            // configuration, and INFO is what that deserves.
            log.info(message);
            return;
        }
        // Credentials that exist but do not work is somebody's mistake, and the
        // symptom is identical to having none - synchronisation simply never
        // happens. WARN so it stands out in a file someone is scrolling through
        // months later, asking why nothing reached the cloud.
        log.warn("Synchronisation is disabled: {}", message);
        log.warn("Records are stored locally and do not leave this machine.");
    }

    public static boolean isFirebaseAvailable() {
        return firebaseAvailable;
    }
}
