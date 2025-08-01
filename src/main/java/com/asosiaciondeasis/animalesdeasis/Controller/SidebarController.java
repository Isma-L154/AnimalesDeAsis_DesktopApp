package com.asosiaciondeasis.animalesdeasis.Controller;

import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class SidebarController {

    // This controller is responsible for the sidebar of the application.
    private PortalController portalController;

    @FXML public Label statsLabel;
    @FXML public Label animalsLabel;

    public SidebarController() {}

    /**
     * When the user clicks on the statsLabel, it will load the StatisticsManagement.fxml file
     * This method initializes the sidebar by setting up event handlers for the labels.
     */

    @FXML
    public void initialize() {
        statsLabel.setOnMouseClicked(e -> {
            if (portalController != null) {
                portalController.loadContent("/fxml/Statistics/StatisticsManagement.fxml");
            }
        });
        animalsLabel.setOnMouseClicked(e -> {
            if (portalController != null) {
                portalController.loadContent("/fxml/Animal/AnimalManagement.fxml");
            }
        });
    }

    public void setPortalController(PortalController portalController) {
        this.portalController = portalController;
    }

}
