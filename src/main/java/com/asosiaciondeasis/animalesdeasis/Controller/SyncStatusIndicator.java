package com.asosiaciondeasis.animalesdeasis.Controller;

import com.asosiaciondeasis.animalesdeasis.Config.FirebaseConfig;
import com.asosiaciondeasis.animalesdeasis.Util.NetworkUtils;
import com.asosiaciondeasis.animalesdeasis.Util.SyncEventManager;
import javafx.application.Platform;
import javafx.scene.control.Label;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Drives the synchronisation chip in the header.
 *
 * <p>Until now the only way to find out whether records had reached Firebase was
 * to start the application from a terminal and read {@code println} output. For
 * an offline-first application that is a significant gap: someone can work all
 * day believing their data is backed up when synchronisation has been failing
 * since the morning.</p>
 *
 * <p><b>Threading.</b> {@link NetworkUtils#isInternetAvailable()} opens sockets
 * and blocks for up to several seconds across its probes, so it can never run on
 * the JavaFX application thread. The poll runs on a single daemon scheduler and
 * only the label update is posted back to the interface. The thread is a daemon
 * so it cannot keep the application alive after the window closes.</p>
 */
public class SyncStatusIndicator {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    /** Connectivity changes on a human timescale; polling harder just burns battery. */
    private static final long POLL_SECONDS = 60;

    private static final String CLASS_SYNCED = "is-synced";
    private static final String CLASS_OFFLINE = "is-offline";
    private static final String CLASS_PENDING = "is-pending";
    private static final List<String> ALL_STATES = List.of(CLASS_SYNCED, CLASS_OFFLINE, CLASS_PENDING);

    private final Label chip;
    private final ScheduledExecutorService scheduler;
    private final Runnable syncListener;

    private volatile String lastSyncTime;

    public SyncStatusIndicator(Label chip) {
        this.chip = chip;
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "sync-status-poll");
            t.setDaemon(true);
            return t;
        });

        // SyncService notifies from a background thread, so this callback must not
        // touch the scene graph directly.
        this.syncListener = () -> {
            lastSyncTime = LocalTime.now().format(TIME);
            refresh();
        };
        SyncEventManager.addListener(syncListener);

        scheduler.scheduleWithFixedDelay(this::refresh, 0, POLL_SECONDS, TimeUnit.SECONDS);
    }

    /** Runs off the interface thread; hands only the final text back to it. */
    private void refresh() {
        String text;
        String state;

        if (!FirebaseConfig.isFirebaseAvailable()) {
            // No credentials bundled, or they failed to decrypt. The application
            // works, but nothing will ever leave this machine, and saying so is
            // the whole point of the chip.
            text = "Solo local";
            state = CLASS_OFFLINE;
        } else if (!NetworkUtils.isInternetAvailable()) {
            text = "Sin conexión";
            state = CLASS_OFFLINE;
        } else if (lastSyncTime != null) {
            text = "Sincronizado " + lastSyncTime;
            state = CLASS_SYNCED;
        } else {
            // Online with credentials, but no synchronisation has completed in
            // this session yet. Claiming "synced" here would be a lie.
            text = "En línea";
            state = CLASS_PENDING;
        }

        final String finalText = text;
        final String finalState = state;
        Platform.runLater(() -> {
            chip.setText(finalText);
            chip.getStyleClass().removeAll(ALL_STATES);
            chip.getStyleClass().add(finalState);
            chip.setAccessibleText("Estado de sincronización: " + finalText);
        });
    }

    /**
     * Stops polling and unsubscribes. The scheduler thread is a daemon, but a
     * listener left on the static registry would keep this object, its label and
     * the whole scene graph reachable for the life of the process.
     */
    public void dispose() {
        SyncEventManager.removeListener(syncListener);
        scheduler.shutdownNow();
    }
}
