package com.asosiaciondeasis.animalesdeasis.Util;

import com.asosiaciondeasis.animalesdeasis.JavaFxToolkit;
import com.asosiaciondeasis.animalesdeasis.Util.Helpers.Toasts;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The non-blocking half of the feedback rework.
 *
 * <p>The failure modes here are quiet ones: a toast shown into a scene with no
 * layer, an overlay that swallows clicks meant for the interface beneath it, or
 * a stack that grows without bound until it covers the window. None of them
 * throw.</p>
 */
class ToastsTest {

    @BeforeAll
    static void startToolkit() throws Exception {
        JavaFxToolkit.start();
    }

    private record Fixture(Scene scene, VBox layer) {
    }

    private static Fixture newScene() {
        VBox layer = new VBox();
        StackPane root = new StackPane(new VBox(), layer);
        Scene scene = new Scene(root, 800, 600);
        Toasts.register(scene, layer);
        return new Fixture(scene, layer);
    }

    @Test
    @DisplayName("a toast is added to the layer of its own scene")
    void toastGoesToTheRegisteredLayer() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            Fixture f = newScene();

            Toasts.success(f.scene().getRoot(), "Animal guardado");

            assertEquals(1, f.layer().getChildren().size());
            assertTrue(f.layer().getChildren().get(0).getStyleClass().contains("toast-success"));
        });
    }

    @Test
    @DisplayName("the message is announced to assistive technology")
    void toastCarriesAnAccessibleName() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            Fixture f = newScene();

            Toasts.info(f.scene().getRoot(), "Filtros limpiados");

            assertEquals("Filtros limpiados",
                    f.layer().getChildren().get(0).getAccessibleText(),
                    "a message that only fades is invisible to a screen reader");
        });
    }

    /**
     * The classic way an overlay breaks an application: a transparent container
     * spanning the window, quietly eating every click.
     */
    @Test
    @DisplayName("a toast never intercepts a click meant for what is underneath")
    void toastsAreMouseTransparent() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            Fixture f = newScene();

            Toasts.warning(f.scene().getRoot(), "Sin conexión");

            assertTrue(f.layer().getChildren().get(0).isMouseTransparent());
        });
    }

    @Test
    @DisplayName("the stack is capped so it cannot cover the window")
    void olderToastsAreDroppedBeyondTheCap() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            Fixture f = newScene();

            for (int i = 1; i <= 9; i++) {
                Toasts.info(f.scene().getRoot(), "Mensaje " + i);
            }

            assertTrue(f.layer().getChildren().size() <= 4,
                    "nine toasts at once would otherwise fill the screen");
        });
    }

    @Test
    @DisplayName("showing into a scene with no layer is a no-op, not a crash")
    void unregisteredSceneIsIgnored() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            VBox orphanRoot = new VBox();
            new Scene(orphanRoot, 400, 300);

            // Must not throw. A confirmation nobody sees is a smaller problem
            // than an exception on a screen outside the portal.
            Toasts.success(orphanRoot, "Guardado");
        });
    }

    @Test
    @DisplayName("a node outside any scene is ignored")
    void nodeWithoutSceneIsIgnored() throws Exception {
        JavaFxToolkit.onFxThread(() -> Toasts.success(new VBox(), "Guardado"));
    }

    @Test
    @DisplayName("blank and null messages produce nothing")
    void emptyMessagesAreDropped() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            Fixture f = newScene();

            Toasts.success(f.scene().getRoot(), "");
            Toasts.success(f.scene().getRoot(), "   ");
            Toasts.success(f.scene().getRoot(), null);

            assertEquals(0, f.layer().getChildren().size());
        });
    }

    /**
     * NavigationHelper falls back to a dialog when this returns false, so the
     * return value is what decides whether a success is announced at all.
     */
    @Test
    @DisplayName("the default-scene route reports whether it could show anything")
    void defaultSceneReportsSuccess() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            Fixture f = newScene();

            assertTrue(Toasts.showOnDefault("Guardado", Toasts.Kind.SUCCESS),
                    "a registered layer means the toast was shown");
            assertEquals(1, f.layer().getChildren().size());
        });
    }

    @Test
    @DisplayName("each kind carries its own styling")
    void kindsAreDistinguishable() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            Fixture f = newScene();

            Toasts.success(f.scene().getRoot(), "ok");
            Toasts.info(f.scene().getRoot(), "info");
            Toasts.warning(f.scene().getRoot(), "cuidado");

            assertEquals(3, f.layer().getChildren().size());
            assertTrue(f.layer().getChildren().get(0).getStyleClass().contains("toast-success"));
            assertTrue(f.layer().getChildren().get(1).getStyleClass().contains("toast-info"));
            assertTrue(f.layer().getChildren().get(2).getStyleClass().contains("toast-warning"));
            assertFalse(f.layer().getChildren().get(0).getStyleClass().contains("toast-warning"));
        });
    }
}
