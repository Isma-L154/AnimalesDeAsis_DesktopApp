package com.asosiaciondeasis.animalesdeasis.Util;

import javafx.concurrent.Task;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Background work owned by one screen: it runs off the JavaFX application
 * thread, and its result is delivered back on that thread only while the screen
 * is still shown.
 *
 * <p>The guard is a flag rather than {@code Task.cancel()}, because cancelling
 * cannot stop a task whose work has already finished while its success
 * hand-off is still queued; without the check that hand-off redraws a detached
 * screen and raises its dialog over the one that replaced it.</p>
 *
 * <p>Work runs on one daemon thread per screen, so submissions complete in
 * order - a reload queued after a delete sees the deletion - and nothing keeps
 * the process alive once the window is closed.</p>
 */
public final class ScreenTasks {

    private final ExecutorService executor;
    /** Written and read on the JavaFX application thread only. */
    private boolean closed;

    public ScreenTasks(String threadName) {
        this.executor = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, threadName);
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Runs {@code work} in the background. Call on the JavaFX application thread;
     * both handlers run there too, and neither runs after {@link #close()}.
     */
    public <T> void submit(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onFailure) {
        if (closed) {
            return;
        }
        Task<T> task = new Task<>() {
            @Override
            protected T call() throws Exception {
                return work.call();
            }
        };
        task.setOnSucceeded(e -> {
            if (!closed) {
                onSuccess.accept(task.getValue());
            }
        });
        task.setOnFailed(e -> {
            if (!closed) {
                onFailure.accept(task.getException());
            }
        });
        executor.submit(task);
    }

    /** Stops delivering results and interrupts whatever is running. Call from the screen's cleanup. */
    public void close() {
        closed = true;
        executor.shutdownNow();
    }
}
