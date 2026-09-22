package com.asosiaciondeasis.animalesdeasis.Util;

import com.asosiaciondeasis.animalesdeasis.JavaFxToolkit;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScreenTasksTest {

    @BeforeAll
    static void startToolkit() throws Exception {
        JavaFxToolkit.start();
    }

    @Test
    void deliversTheResultOnTheJavaFxThread() throws Exception {
        ScreenTasks tasks = new ScreenTasks("test");
        CountDownLatch delivered = new CountDownLatch(1);
        AtomicReference<String> result = new AtomicReference<>();
        AtomicBoolean onFxThread = new AtomicBoolean();

        JavaFxToolkit.onFxThread(() -> tasks.submit(() -> "done", value -> {
            result.set(value);
            onFxThread.set(Platform.isFxApplicationThread());
            delivered.countDown();
        }, error -> { }));

        assertTrue(delivered.await(5, TimeUnit.SECONDS));
        assertEquals("done", result.get());
        assertTrue(onFxThread.get());
        tasks.close();
    }

    @Test
    void deliversFailuresToTheFailureHandler() throws Exception {
        ScreenTasks tasks = new ScreenTasks("test");
        CountDownLatch failed = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();

        JavaFxToolkit.onFxThread(() -> tasks.submit(() -> {
            throw new IllegalStateException("database locked");
        }, value -> { }, e -> {
            error.set(e);
            failed.countDown();
        }));

        assertTrue(failed.await(5, TimeUnit.SECONDS));
        assertEquals("database locked", error.get().getMessage());
        tasks.close();
    }

    /**
     * The regression. A screen left while its query ran used to receive the result
     * anyway, redraw controls nobody could see, and raise its dialog over the screen
     * that replaced it.
     */
    @Test
    void deliversNothingOnceClosed() throws Exception {
        ScreenTasks tasks = new ScreenTasks("test");
        CountDownLatch workStarted = new CountDownLatch(1);
        CountDownLatch releaseWork = new CountDownLatch(1);
        AtomicBoolean handlerRan = new AtomicBoolean();

        JavaFxToolkit.onFxThread(() -> tasks.submit(() -> {
            workStarted.countDown();
            releaseWork.await();
            return "late";
        }, value -> handlerRan.set(true), error -> handlerRan.set(true)));

        assertTrue(workStarted.await(5, TimeUnit.SECONDS));
        JavaFxToolkit.onFxThread(tasks::close);
        releaseWork.countDown();

        // Let any pending hand-off to the JavaFX thread run before checking.
        Thread.sleep(200);
        JavaFxToolkit.onFxThread(() -> { });
        assertFalse(handlerRan.get());
    }

    @Test
    void ignoresWorkSubmittedAfterClose() throws Exception {
        ScreenTasks tasks = new ScreenTasks("test");
        AtomicBoolean ran = new AtomicBoolean();

        JavaFxToolkit.onFxThread(() -> {
            tasks.close();
            tasks.submit(() -> {
                ran.set(true);
                return null;
            }, value -> { }, error -> { });
        });

        Thread.sleep(200);
        assertFalse(ran.get());
    }
}
