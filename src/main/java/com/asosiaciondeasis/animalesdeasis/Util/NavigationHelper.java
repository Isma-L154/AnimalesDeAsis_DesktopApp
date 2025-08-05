package com.asosiaciondeasis.animalesdeasis.Util;

import com.asosiaciondeasis.animalesdeasis.Controller.PortalController;
import javafx.scene.control.Alert;

public class NavigationHelper {

    public static void goToAnimalModule(PortalController portalController){

        if (portalController != null) {
            portalController.loadContent("/fxml/Animal/AnimalManagement.fxml");
        } else {
            showError("No se pudo cambiar de módulo");
        }
    }

    private static void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
