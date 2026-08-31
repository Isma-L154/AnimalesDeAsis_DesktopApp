package com.asosiaciondeasis.animalesdeasis.Util.Helpers;

import com.asosiaciondeasis.animalesdeasis.Controller.PortalController;
import com.asosiaciondeasis.animalesdeasis.Model.NavigationSection;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
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

    private static void setupAlertStyle(Alert alert, String title, String header, String content) {
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);


        try {
            Image icon = new Image(NavigationHelper.class.getResourceAsStream(COMPANY_ICON_PATH));
            Stage stage = (Stage) alert.getDialogPane().getScene().getWindow();
            stage.getIcons().add(icon);
        } catch (Exception e) {
            log.info("No se pudo cargar el icono: "+ e.getMessage());
        }


        alert.getDialogPane().getStylesheets().add(
                NavigationHelper.class.getResource("/css/Alerts.css").toExternalForm()
        );
        alert.getDialogPane().getStyleClass().add("custom-alert");
    }
}
