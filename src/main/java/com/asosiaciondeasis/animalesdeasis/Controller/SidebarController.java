package com.asosiaciondeasis.animalesdeasis.Controller;

import com.asosiaciondeasis.animalesdeasis.Abstraccions.IPortalAwareController;
import javafx.fxml.FXML;
import javafx.scene.control.Label;

public class SidebarController implements IPortalAwareController {

    @FXML public Label statsLabel;
    @FXML public Label animalsLabel;
    // This controller is responsible for the sidebar of the application.
    private PortalController portalController;

    public SidebarController() {
    }

    /**
     * When the user clicks on the statsLabel, it will load the StatisticsManagement.fxml file
     * This method initializes the sidebar by setting up event handlers for the labels.
     */

    /** Style class that highlights the navigation item of the current screen. */
    private static final String ACTIVE_CLASS = "active";

    @FXML
    public void initialize() {
        statsLabel.setOnMouseClicked(e -> {
            if (portalController != null) {
                portalController.loadContent("/fxml/Statistics/StatisticsManagement.fxml");
                markActive(statsLabel);
            }
        });
        animalsLabel.setOnMouseClicked(e -> {
            if (portalController != null) {
                portalController.loadContent("/fxml/Animal/AnimalManagement.fxml");
                markActive(animalsLabel);
            }
        });
    }

    /**
     * Marks {@code selected} as the active navigation item and clears the flag from
     * the others, so the sidebar always reflects which screen is open.
     */
    private void markActive(Label selected) {
        for (Label item : new Label[]{statsLabel, animalsLabel}) {
            item.getStyleClass().remove(ACTIVE_CLASS);
        }
        if (!selected.getStyleClass().contains(ACTIVE_CLASS)) {
            selected.getStyleClass().add(ACTIVE_CLASS);
        }
    }

    @Override
    public void setPortalController(PortalController portalController) {
        this.portalController = portalController;
    }

}
