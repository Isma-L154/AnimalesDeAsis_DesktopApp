package com.asosiaciondeasis.animalesdeasis;

import javafx.application.Platform;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Starts the JavaFX runtime once for the whole test run, and runs code on its
 * application thread.
 *
 * <p>Needed because a {@code Control} cannot even be constructed without it —
 * {@code new TextField()} throws {@code ExceptionInInitializerError} on a plain
 * JVM. That is why none of the interface behaviour in this project had automated
 * coverage: not because it was hard to test, but because the tests could not get
 * far enough to start.</p>
 *
 * <p>On a build agent there is no display, so the workflow runs the suite under
 * {@code xvfb-run}. Real JavaFX against a virtual X server, rather than a
 * stubbed toolkit, so what the tests exercise is what ships.</p>
 */
public final class JavaFxToolkit {

    private static boolean started;

    private JavaFxToolkit() {
    }

    /** Idempotent: JavaFX permits exactly one startup per JVM. */
    public static synchronized void start() throws Exception {
        if (started) {
            return;
        }
        CountDownLatch ready = new CountDownLatch(1);
        try {
            Platform.startup(ready::countDown);
        } catch (IllegalStateException alreadyRunning) {
            // Another test class got here first within the same JVM.
            started = true;
            return;
        }
        if (!ready.await(15, TimeUnit.SECONDS)) {
            throw new IllegalStateException("JavaFX did not start within 15 seconds");
        }
        // Without this the runtime shuts down as soon as the last window closes,
        // and a later test class cannot start it again.
        Platform.setImplicitExit(false);
        started = true;
    }

    /**
     * Runs {@code action} on the application thread and waits for it, rethrowing
     * whatever it threw.
     *
     * <p>Waiting matters: without it a test asserts against a scene graph that
     * has not been touched yet, and passes or fails depending on timing.</p>
     */
    public static void onFxThread(ThrowingRunnable action) throws Exception {
        start();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                action.run();
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                done.countDown();
            }
        });
        if (!done.await(15, TimeUnit.SECONDS)) {
            throw new IllegalStateException("action did not complete on the JavaFX thread");
        }
        Throwable t = failure.get();
        if (t instanceof Exception e) {
            throw e;
        }
        if (t != null) {
            throw new RuntimeException(t);
        }
    }

    @FunctionalInterface
    public interface ThrowingRunnable {
        void run() throws Exception;
    }
}
