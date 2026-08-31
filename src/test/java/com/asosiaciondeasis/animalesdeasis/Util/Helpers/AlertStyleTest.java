package com.asosiaciondeasis.animalesdeasis.Util.Helpers;

import com.asosiaciondeasis.animalesdeasis.JavaFxToolkit;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The dialogs that still block, and the regression that made them unusable.
 *
 * <p>A {@link javafx.scene.control.Dialog} gets its own {@code Scene}, so it
 * inherits nothing from the main window — including the looked-up colours
 * declared on its {@code .root}. When {@code Alerts.css} moved from hex literals
 * to tokens, it stopped being able to stand alone, and nothing said so: JavaFX
 * logged a {@code ClassCastException} per property and carried on drawing a
 * white button with white text on a white dialog.</p>
 *
 * <p>Everything here checks a property that failure leaves silent.</p>
 */
class AlertStyleTest {

    @BeforeAll
    static void startToolkit() throws Exception {
        JavaFxToolkit.start();
    }

    /** Lays the dialog out so the stylesheets are actually resolved. */
    private static DialogPane realised(Alert alert) {
        DialogPane pane = alert.getDialogPane();
        pane.applyCss();
        pane.layout();
        return pane;
    }

    @Test
    @DisplayName("the dialog loads the stylesheet that defines its colours")
    void themeIsLoadedIntoTheDialog() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            Alert alert = NavigationHelper.buildAlert(Alert.AlertType.ERROR,
                    "Error", "No se pudo guardar", "Detalle.");

            assertTrue(alert.getDialogPane().getStylesheets().stream()
                            .anyMatch(url -> url.endsWith("theme.css")),
                    "without theme.css every token in Alerts.css fails to resolve, and the "
                            + "buttons come out white on white");
        });
    }

    /**
     * The symptom the user actually reported: buttons that were not there.
     */
    @Test
    @DisplayName("the buttons have a visible background")
    void buttonsAreNotInvisible() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            Alert alert = NavigationHelper.buildAlert(Alert.AlertType.CONFIRMATION,
                    "Confirmar", "¿Eliminar el animal?", "Esta acción no se puede deshacer.");
            DialogPane pane = realised(alert);

            Button ok = (Button) pane.lookupButton(ButtonType.OK);
            assertNotNull(ok, "the dialog has no OK button");
            assertNotNull(ok.getBackground(), "the button has no background at all");
            assertFalse(ok.getBackground().getFills().isEmpty(),
                    "no fill means nothing is painted: an invisible button");

            Color fill = (Color) ok.getBackground().getFills().get(0).getFill();
            assertNotEquals(Color.TRANSPARENT, fill, "a transparent button is an invisible one");
            assertNotEquals(Color.WHITE, fill,
                    "white on a white dialog is what this test exists to catch");
        });
    }

    /**
     * JavaFX gives an ERROR dialog a large cross, in the corner where a window's
     * close control lives. It reads as "click to dismiss" and does nothing.
     */
    @Test
    @DisplayName("no decorative graphic that looks like a close button")
    void noMisleadingGraphic() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            for (Alert.AlertType type : new Alert.AlertType[]{
                    Alert.AlertType.ERROR, Alert.AlertType.WARNING,
                    Alert.AlertType.INFORMATION, Alert.AlertType.CONFIRMATION}) {
                Alert alert = NavigationHelper.buildAlert(type, "T", "H", "C");
                assertNull(alert.getGraphic(),
                        type + " still carries a graphic that looks like a dismiss control");
            }
        });
    }

    @Test
    @DisplayName("buttons are labelled in Spanish")
    void buttonsAreLocalised() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            Alert alert = NavigationHelper.buildAlert(Alert.AlertType.CONFIRMATION,
                    "Confirmar", "¿Seguro?", "Detalle.");
            DialogPane pane = realised(alert);

            assertEquals("Aceptar", ((Button) pane.lookupButton(ButtonType.OK)).getText());
            assertEquals("Cancelar", ((Button) pane.lookupButton(ButtonType.CANCEL)).getText());
        });
    }

    /**
     * Confirming and dismissing were drawn identically, so neither read as the
     * action being asked for.
     */
    @Test
    @DisplayName("the confirming button is distinguishable from the way out")
    void primaryAndSecondaryDiffer() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            Alert alert = NavigationHelper.buildAlert(Alert.AlertType.CONFIRMATION,
                    "Confirmar", "¿Seguro?", "Detalle.");
            DialogPane pane = realised(alert);

            Button ok = (Button) pane.lookupButton(ButtonType.OK);
            Button cancel = (Button) pane.lookupButton(ButtonType.CANCEL);

            assertTrue(ok.getStyleClass().contains("alert-primary"));
            assertTrue(cancel.getStyleClass().contains("alert-secondary"));

            Color okFill = (Color) ok.getBackground().getFills().get(0).getFill();
            Color cancelFill = (Color) cancel.getBackground().getFills().get(0).getFill();
            assertNotEquals(okFill, cancelFill,
                    "if both are painted the same, neither is the main action");
        });
    }

    @Test
    @DisplayName("the cancel button is readable against its own background")
    void cancelButtonHasContrast() throws Exception {
        JavaFxToolkit.onFxThread(() -> {
            Alert alert = NavigationHelper.buildAlert(Alert.AlertType.CONFIRMATION,
                    "Confirmar", "¿Seguro?", "Detalle.");
            DialogPane pane = realised(alert);

            Button cancel = (Button) pane.lookupButton(ButtonType.CANCEL);
            Color background = (Color) cancel.getBackground().getFills().get(0).getFill();
            Color text = (Color) cancel.getTextFill();

            // The failure mode was white text on a white button. Any real
            // difference in brightness rules that out.
            double difference = Math.abs(brightness(background) - brightness(text));
            assertTrue(difference > 0.25,
                    "text and background are too close to tell apart: background=" + background
                            + " text=" + text);
        });
    }

    private static double brightness(Color c) {
        return 0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue();
    }
}
