package com.asosiaciondeasis.animalesdeasis.Config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;

import java.io.InputStream;


public class FirebaseConfig {

    // Flag to ensure Firebase is initialized only once
    private static boolean initialized = false;

    public static void initialize() {
        if (initialized) return;

        try {
            // Load the service account JSON from resources folder
            InputStream serviceAccount = FirebaseConfig.class.getResourceAsStream("/FireConfig/animalesdeasis-6bfd7-firebase-adminsdk-fbsvc-0d6981b8e6.json");

            if (serviceAccount == null) {
                throw new RuntimeException("JSON not found");
            }

            // Build Firebase options using the credentials from the JSON file
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();

            // Initialize Firebase with the options
            FirebaseApp.initializeApp(options);
            initialized = true;
            System.out.println("✅ Firebase initialized successfully");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("Error initializing Firebase");
        }
    }
}
