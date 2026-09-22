package com.asosiaciondeasis.animalesdeasis;

import com.asosiaciondeasis.animalesdeasis.Config.FirebaseConfig;
import com.asosiaciondeasis.animalesdeasis.Config.SQLiteSetup;
import com.asosiaciondeasis.animalesdeasis.Config.ServiceFactory;
import com.asosiaciondeasis.animalesdeasis.Service.SyncService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Prepares the database and, when credentials allow it, starts synchronisation. */
public final class AppInitializer {
    private static final Logger log = LoggerFactory.getLogger(AppInitializer.class);

    /** Sync once at startup, then every 24 hours for as long as the app stays open. */
    private static final long SYNC_INTERVAL_MS = TimeUnit.HOURS.toMillis(24);

    private AppInitializer() {
    }

    /**
     * Blocks on disk and network: call it from a background thread.
     *
     * @param progress told what is happening, in words fit for the splash screen
     */
    public static void initializeApp(Consumer<String> progress) throws Exception {
        progress.accept("Configurando base de datos...");
        SQLiteSetup.initializeDatabase();

        progress.accept("Conectando con Firebase...");
        if (!FirebaseConfig.initialize()) {
            log.info("Running in offline-only mode - no sync available");
            return;
        }

        progress.accept("Sincronizando datos...");
        SyncService syncService = ServiceFactory.getSyncService();
        syncService.sync();
        schedulePeriodicSync(syncService);
    }

    private static void schedulePeriodicSync(SyncService syncService) {
        // A daemon, so a pending run never keeps the application alive on exit.
        Timer timer = new Timer("periodic-sync", true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                syncService.sync();
            }
        }, SYNC_INTERVAL_MS, SYNC_INTERVAL_MS);
    }
}
