package com.asosiaciondeasis.animalesdeasis.Util.Helpers;

import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.animation.SequentialTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.util.Duration;
import org.kordamp.ikonli.javafx.FontIcon;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Brief, non-blocking confirmations that fade on their own.
 *
 * <p>Replaces the modal dialog that used to announce success. Saving an animal
 * raised a window that had to be dismissed before anything else could happen —
 * a confirmation that costs a click is a confirmation that interrupts, and when
 * the answer is "yes, that worked" the interruption buys nothing.</p>
 *
 * <p>Toasts are for things that went right and for information the user does not
 * have to act on. Anything they must decide, or a failure that leaves work
 * undone, still stops them: see {@link NavigationHelper}.</p>
 *
 * <h2>Where they appear</h2>
 * <p>Each scene has one toast layer, registered by whoever owns that scene's
 * layout. The registry is a {@link WeakHashMap} keyed by {@link Scene}, so a
 * window that is closed takes its entry with it rather than leaking the layer
 * and everything the scene holds.</p>
 *
 * <p>If no layer is registered — a screen outside the portal — the message is
 * dropped rather than forcing a dialog. A confirmation nobody sees is a smaller
 * problem than one that blocks a window it was not designed for.</p>
 */
public final class Toasts {

    /** Long enough to read a short sentence, short enough not to linger. */
    private static final Duration VISIBLE = Duration.seconds(3);
    private static final Duration FADE = Duration.millis(220);
    /** Beyond this, older toasts are removed so the stack cannot cover the screen. */
    private static final int MAX_VISIBLE = 4;

    public enum Kind {
        SUCCESS("toast-success", "fas-check-circle"),
        INFO("toast-info", "fas-info-circle"),
        WARNING("toast-warning", "fas-exclamation-circle");

        private final String styleClass;
        private final String icon;

        Kind(String styleClass, String icon) {
            this.styleClass = styleClass;
            this.icon = icon;
        }
    }

    private static final Map<Scene, Pane> LAYERS = new WeakHashMap<>();

    /**
     * The scene toasts go to when the caller has no node to offer.
     *
     * <p>This application shows one main window, so "the scene" is unambiguous in
     * practice. Held weakly for the same reason as the registry: a closed window
     * must not be kept alive by a static field. Callers that do have a node
     * should pass it — this is the fallback, not the intended route.</p>
     */
    private static java.lang.ref.WeakReference<Scene> defaultScene =
            new java.lang.ref.WeakReference<>(null);

    private Toasts() {
    }

    /** Registers the container that holds this scene's toasts. */
    public static void register(Scene scene, Pane layer) {
        if (scene != null && layer != null) {
            LAYERS.put(scene, layer);
            defaultScene = new java.lang.ref.WeakReference<>(scene);
        }
    }

    /** Success on the main window, for callers without a node to hand. */
    public static void success(String message) {
        showOnDefault(message, Kind.SUCCESS);
    }

    /** Information on the main window, for callers without a node to hand. */
    public static void info(String message) {
        showOnDefault(message, Kind.INFO);
    }

    /** A caution on the main window, for callers without a node to hand. */
    public static void warning(String message) {
        showOnDefault(message, Kind.WARNING);
    }

    /**
     * @return whether a toast could actually be shown, so callers can fall back
     *         to a dialog rather than silently saying nothing
     */
    public static boolean showOnDefault(String message, Kind kind) {
        Scene scene = defaultScene.get();
        if (scene == null || !LAYERS.containsKey(scene)) {
            return false;
        }
        show(scene.getRoot(), message, kind);
        return true;
    }

    public static void success(Node anyNodeInScene, String message) {
        show(anyNodeInScene, message, Kind.SUCCESS);
    }

    public static void info(Node anyNodeInScene, String message) {
        show(anyNodeInScene, message, Kind.INFO);
    }

    public static void warning(Node anyNodeInScene, String message) {
        show(anyNodeInScene, message, Kind.WARNING);
    }

    /**
     * @param anyNodeInScene any node belonging to the scene the toast should
     *                       appear in; used only to find that scene
     */
    public static void show(Node anyNodeInScene, String message, Kind kind) {
        if (anyNodeInScene == null || message == null || message.isBlank()) {
            return;
        }
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(() -> show(anyNodeInScene, message, kind));
            return;
        }
        Scene scene = anyNodeInScene.getScene();
        if (scene == null) {
            return;
        }
        Pane layer = LAYERS.get(scene);
        if (layer == null) {
            return;
        }

        HBox toast = build(message, kind);
        layer.getChildren().add(toast);
        while (layer.getChildren().size() > MAX_VISIBLE) {
            layer.getChildren().remove(0);
        }

        FadeTransition in = new FadeTransition(FADE, toast);
        in.setFromValue(0);
        in.setToValue(1);

        FadeTransition out = new FadeTransition(FADE, toast);
        out.setFromValue(1);
        out.setToValue(0);

        SequentialTransition life =
                new SequentialTransition(in, new PauseTransition(VISIBLE), out);
        life.setOnFinished(e -> layer.getChildren().remove(toast));
        life.play();
    }

    private static HBox build(String message, Kind kind) {
        FontIcon icon = new FontIcon(kind.icon);
        icon.getStyleClass().add("toast-icon");

        Label text = new Label(message);
        text.getStyleClass().add("toast-text");
        text.setWrapText(true);

        HBox toast = new HBox(9, icon, text);
        toast.getStyleClass().addAll("toast", kind.styleClass);
        toast.setOpacity(0);
        toast.setMaxWidth(360);
        // A toast must never swallow a click meant for what is underneath it.
        toast.setMouseTransparent(true);
        // Screen readers get told; sighted users get the fade.
        toast.setAccessibleText(message);
        return toast;
    }
}
