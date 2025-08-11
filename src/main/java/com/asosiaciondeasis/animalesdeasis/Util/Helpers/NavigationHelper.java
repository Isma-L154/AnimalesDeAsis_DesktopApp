package com.asosiaciondeasis.animalesdeasis.Util.Helpers;

import com.asosiaciondeasis.animalesdeasis.Controller.PortalController;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.image.Image;
import javafx.stage.Stage;

public class NavigationHelper {

    private static final String COMPANY_ICON_PATH = "/images/AdeAsisLogo.png";

    public static void goToAnimalModule(PortalController portalController) {
        if (portalController != null) {
            portalController.loadContent("/fxml/Animal/AnimalManagement.fxml");
        } else {
            showErrorAlert("Error", "No se pudo cambiar de módulo", "El controlador del portal es nulo.");
        }
    }

    public static void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        setupAlertStyle(alert, title, header, content);
        alert.showAndWait();
    }

    public static void showInfoAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        setupAlertStyle(alert, title, null, message);
        alert.showAndWait();
    }

    public static void showWarningAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        setupAlertStyle(alert, title, null, message);
        alert.showAndWait();
    }

    public static void showSuccessAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        setupAlertStyle(alert, title, null, message);


        alert.getDialogPane().getStylesheets().add(
                NavigationHelper.class.getResource("/css/Alerts.css").toExternalForm()
        );
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
            System.out.println("No se pudo cargar el icono: " + e.getMessage());
        }


        alert.getDialogPane().getStylesheets().add(
                NavigationHelper.class.getResource("/css/Alerts.css").toExternalForm()
        );
        alert.getDialogPane().getStyleClass().add("custom-alert");
    }
}
