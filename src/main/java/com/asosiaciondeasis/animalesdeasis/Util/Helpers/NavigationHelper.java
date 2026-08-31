package com.asosiaciondeasis.animalesdeasis.Util.Helpers;

import com.asosiaciondeasis.animalesdeasis.Controller.PortalController;
import com.asosiaciondeasis.animalesdeasis.Model.NavigationSection;
import javafx.scene.Node;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.DialogPane;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NavigationHelper {
    private static final Logger log = LoggerFactory.getLogger(NavigationHelper.class);


    private static final String COMPANY_ICON_PATH = "/images/AdeAsisLogo.png";

    public static void goToAnimalModule(PortalController portalController) {
        if (portalController != null) {
            portalController.navigateTo(NavigationSection.ANIMALS);
        } else {
            showErrorAlert("Error", "No se pudo cambiar de módulo", "El controlador del portal es nulo.");
        }
    }

    /**
     * A failure that left work undone.
     *
     * <p>Deliberately still a dialog. The rule this class follows is that only
     * two things may block: a decision the user must make, and a failure they
     * need to know about before carrying on. Everything else fades.</p>
     *
     * <p>Form validation is neither, and no longer arrives here — a rejected
     * field is marked in place by {@link FieldValidation}, next to the field,
     * with the form still usable.</p>
     */
    public static void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        setupAlertStyle(alert, title, header, content);
        alert.showAndWait();
    }

    /** Information the user does not have to act on. Same reasoning as success. */
    public static void showInfoAlert(String title, String message) {
        if (Toasts.showOnDefault(message, Toasts.Kind.INFO)) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        setupAlertStyle(alert, title, null, message);
        alert.showAndWait();
    }

    /**
     * A caution that does not stop the work.
     *
     * <p>Distinct from {@link #showErrorAlert}, which stays a dialog: a warning
     * says something is worth knowing, an error says something did not happen.
     * Only the second one has earned the right to block.</p>
     */
    public static void showWarningAlert(String title, String message) {
        if (Toasts.showOnDefault(message, Toasts.Kind.WARNING)) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.WARNING);
        setupAlertStyle(alert, title, null, message);
        alert.showAndWait();
    }

    /**
     * Confirms something that worked.
     *
     * <p>A toast now, not a dialog. Saving a record used to raise a window that
     * had to be dismissed before anything else could happen; a confirmation that
     * costs a click interrupts, and when the answer is "that worked" the
     * interruption buys nothing.</p>
     *
     * <p>Falls back to the dialog only when no toast layer is reachable — a
     * separate window, say. Better an old-style dialog than a confirmation
     * nobody ever sees.</p>
     */
    public static void showSuccessAlert(String title, String message) {
        if (Toasts.showOnDefault(message, Toasts.Kind.SUCCESS)) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        setupAlertStyle(alert, title, null, message);
        alert.getDialogPane().getStyleClass().add("success-alert");
        alert.showAndWait();
    }

    public static boolean showConfirmationAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        setupAlertStyle(alert, title, header, content);

        return alert.showAndWait()
                .filter(response -> response == ButtonType.OK)
                .isPresent();
    }

    /**
     * Builds a styled alert without showing it.
     *
     * <p>Package-visible so a test can inspect the result. Everything that made
     * these dialogs unreadable - a missing stylesheet, a graphic that looked like
     * a close button, buttons with no visible hierarchy - is decided here, and
     * none of it could be checked while the only way to obtain a dialog was to
     * open one and block on it.</p>
     */
    static Alert buildAlert(Alert.AlertType type, String title, String header, String content) {
        Alert alert = new Alert(type);
        setupAlertStyle(alert, title, header, content);
        return alert;
    }

    private static void setupAlertStyle(Alert alert, String title, String header, String content) {
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        DialogPane pane = alert.getDialogPane();

        /*
         * theme.css first, then Alerts.css.
         *
         * A Dialog gets its own Scene, so it inherits nothing from the main
         * window - including the looked-up colours declared on its `.root`.
         * Alerts.css refers to them by name, so without theme.css here every one
         * of them fails to resolve and JavaFX reports:
         *
         *   ClassCastException: String cannot be cast to javafx.scene.paint.Paint
         *   while converting value for '-fx-background-color'
         *
         * The visible result was a white button with white text on a white
         * dialog, and a header nobody could read. Alerts.css used to hold hex
         * literals and stood alone; it stopped standing alone when those became
         * tokens, and this line is what it needed at that point.
         */
        pane.getStylesheets().addAll(
                NavigationHelper.class.getResource("/css/theme.css").toExternalForm(),
                NavigationHelper.class.getResource("/css/Alerts.css").toExternalForm()
        );
        pane.getStyleClass().add("custom-alert");

        /*
         * No decorative graphic. JavaFX gives an ERROR dialog a large red cross,
         * placed in the top corner where a window's close control lives, so it
         * reads as "click this to dismiss" and does nothing at all. The header
         * text and the coloured bar already say what kind of message this is.
         */
        alert.setGraphic(null);

        // The buttons otherwise come out as "Ok" and "Cancel" from the JVM's
        // default locale, in an application that is Spanish everywhere else.
        localiseButtons(pane);

        try {
            Image icon = new Image(NavigationHelper.class.getResourceAsStream(COMPANY_ICON_PATH));
            Stage stage = (Stage) pane.getScene().getWindow();
            stage.getIcons().add(icon);
        } catch (Exception e) {
            log.info("No se pudo cargar el icono de la ventana: {}", e.getMessage());
        }
    }

    /**
     * Spanish labels, and a visible hierarchy between the buttons.
     *
     * <p>Style classes are assigned here rather than left to the {@code :default}
     * and {@code :cancel-button} pseudo-classes. Those did not both match in
     * practice - the cancel rule never applied, so dismissing and confirming were
     * drawn in the same brand orange and neither read as the main action.
     * Assigning the class is deterministic.</p>
     */
    private static void localiseButtons(DialogPane pane) {
        for (ButtonType type : pane.getButtonTypes()) {
            Node node = pane.lookupButton(type);
            if (!(node instanceof Button button)) {
                continue;
            }
            if (type == ButtonType.OK) {
                button.setText("Aceptar");
                button.getStyleClass().add("alert-primary");
            } else if (type == ButtonType.YES) {
                button.setText("Sí");
                button.getStyleClass().add("alert-primary");
            } else if (type == ButtonType.CANCEL) {
                button.setText("Cancelar");
                button.getStyleClass().add("alert-secondary");
            } else if (type == ButtonType.NO) {
                button.setText("No");
                button.getStyleClass().add("alert-secondary");
            } else if (type == ButtonType.CLOSE) {
                button.setText("Cerrar");
                button.getStyleClass().add("alert-secondary");
            }
        }
    }
}
