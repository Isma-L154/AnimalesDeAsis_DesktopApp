package com.asosiaciondeasis.animalesdeasis;

import com.asosiaciondeasis.animalesdeasis.Service.SyncService;
import com.asosiaciondeasis.animalesdeasis.Util.NetworkUtils;
import com.asosiaciondeasis.animalesdeasis.Config.*;

import java.sql.Connection;
import java.util.Timer;
import java.util.TimerTask;

public class AppInitializer {

    /** 24 Hours in milliseconds, because we need the sync with Firebase everytime the app initialize
     * or every 24 hours.
     * */
    private static final long SYNC_INTERVAL_MS = 24 * 60 * 60 * 1000;
    private static SyncService syncService;

    public static void initializeApp(){
        try{
            //Initialize db for the first time
            SQLiteSetup.initializeDatabase();

            //SQLite Connection
            Connection conn = DatabaseConnection.getConnection();

            //Initialize Firebase in here once
            syncService = new SyncService(conn);

            /** Sync if there's internet */
            if(NetworkUtils.isInternetAvailable()){
                syncService.sync();
            }else{
                System.out.println("No internet connection available");
            }

            schedulePeriodicSync();
            System.out.println("App Initialized");

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }


    private static void schedulePeriodicSync(){
        Timer timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if(NetworkUtils.isInternetAvailable()){
                    syncService.sync();
                }else{
                    System.out.println("No internet connection available");
                }
            }
            // First run after 24h, then every 24h
        }, SYNC_INTERVAL_MS , SYNC_INTERVAL_MS);

    }
}
