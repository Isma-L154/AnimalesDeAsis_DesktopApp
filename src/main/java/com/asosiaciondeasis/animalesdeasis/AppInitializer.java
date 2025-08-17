package com.asosiaciondeasis.animalesdeasis;

import com.asosiaciondeasis.animalesdeasis.Config.DatabaseConnection;
import com.asosiaciondeasis.animalesdeasis.Config.FirebaseConfig;
import com.asosiaciondeasis.animalesdeasis.Config.SQLiteSetup;
import com.asosiaciondeasis.animalesdeasis.Service.SyncService;
import com.asosiaciondeasis.animalesdeasis.Util.NetworkUtils;

import java.sql.Connection;
import java.util.Timer;
import java.util.TimerTask;

public class AppInitializer {

    /**
     * 24 Hours in milliseconds, because we need the sync with Firebase everytime the app initializes
     * or every 24 hours.
     */
    private static final long SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000;
    private static SyncService syncService;
    private static boolean firebaseEnabled = false;

    public static void initializeApp() {
        try {
            //Initialize db for the first time
            SQLiteSetup.initializeDatabase();

            //SQLite Connection
            Connection conn = DatabaseConnection.getConnection();

            //Initialize Firebase
            firebaseEnabled = FirebaseConfig.initialize();



            // Only initialize sync service if Firebase is available
            if (firebaseEnabled) {
                syncService = new SyncService(conn);

                if (NetworkUtils.isInternetAvailable()) {
                    syncService.sync();
                } else {
                    System.out.println("No internet connection available");
                }

                schedulePeriodicSync();
            } else {
                System.out.println("📱 Running in offline-only mode - no sync available");
            }

            System.out.println("✅ App Initialized");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private static void schedulePeriodicSync() {
        if (!firebaseEnabled) return;
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (NetworkUtils.isInternetAvailable()) {
                    syncService.sync();
                } else {
                    System.out.println("No internet connection available");
                }
            }
            // First run after 24h, then every 24h
        }, SYNC_INTERVAL_MS, SYNC_INTERVAL_MS);

    }
}
