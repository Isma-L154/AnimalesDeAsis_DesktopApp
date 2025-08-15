package com.asosiaciondeasis.animalesdeasis.Config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import java.io.InputStream;


public class FirebaseConfig {

    // Flag to ensure Firebase is initialized only once
    private static boolean initialized = false;
    private static boolean firebaseAvailable = false;

    public static boolean initialize() {
        if (initialized) return firebaseAvailable;

        try {
            // Load the service account JSON from the resources folder
            InputStream serviceAccount = CredentialsManager.getDecryptedCredentials();
            if (serviceAccount == null) {
                System.out.println("Firebase credentials not found - running in offline mode");
                initialized = true;
                firebaseAvailable = false;
                return false;
            }

            // Build Firebase options using the credentials from the JSON file
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            // Initialize Firebase with the options
            FirebaseApp.initializeApp(options);
            initialized = true;
            firebaseAvailable = true;
            System.out.println("✅ Firebase initialized successfully");
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            initialized = true;
            firebaseAvailable = false;
            return false;
        }
    }
    public static boolean isFirebaseAvailable() {
        return firebaseAvailable;
    }
}
