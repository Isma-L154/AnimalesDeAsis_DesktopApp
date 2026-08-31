package com.asosiaciondeasis.animalesdeasis.Config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

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

    private static boolean initialized = false;
    private static boolean firebaseAvailable = false;
    private static String unavailableReason;

    private FirebaseConfig() {
    }

    public static boolean initialize() {
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
            unavailableReason = null;
            System.out.println("Firebase inicializado: la sincronización está disponible.");
            return true;

        } catch (CredentialsException e) {
            firebaseAvailable = false;
            unavailableReason = e.getMessage();
            report(e.reason(), e.getMessage());
            return false;

        } catch (Exception e) {
            firebaseAvailable = false;
            unavailableReason = "No se pudo inicializar Firebase: " + e.getMessage();
            System.out.println("[Firebase] " + unavailableReason);
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
            System.out.println("[Firebase] " + message);
            return;
        }
        System.out.println("======================================================================");
        System.out.println("[Firebase] La sincronización está DESACTIVADA.");
        System.out.println("[Firebase] " + message);
        System.out.println("[Firebase] Los datos se guardan localmente y no salen de esta máquina.");
        System.out.println("======================================================================");
    }

    public static boolean isFirebaseAvailable() {
        return firebaseAvailable;
    }

    /**
     * Why synchronisation is off, or {@code null} when it is on. Exists so the
     * interface can say more than "offline" — the header's chip already
     * distinguishes having no credentials from having no connection.
     */
    public static String unavailableReason() {
        return unavailableReason;
    }

    /** Resets the cached state. For tests, which must not inherit a previous run's. */
    static void resetForTests() {
        initialized = false;
        firebaseAvailable = false;
        unavailableReason = null;
    }
}
