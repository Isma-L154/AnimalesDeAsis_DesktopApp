package com.asosiaciondeasis.animalesdeasis.Util.Helpers;

import com.asosiaciondeasis.animalesdeasis.Controller.PortalController;
import javafx.scene.control.Alert;

public class NavigationHelper {

    public static void goToAnimalModule(PortalController portalController) {

        if (portalController != null) {
            portalController.loadContent("/fxml/Animal/AnimalManagement.fxml");
        } else {
            showErrorAlert("Error", "No se pudo cambiar de módulo", "El controlador del portal es nulo.");
        }
    }

    public static void showErrorAlert(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    public static void showInfoAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    public static void showWarningAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
