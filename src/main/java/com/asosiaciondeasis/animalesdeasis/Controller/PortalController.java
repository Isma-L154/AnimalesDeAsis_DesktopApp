package com.asosiaciondeasis.animalesdeasis.Controller;


import com.asosiaciondeasis.animalesdeasis.Abstraccions.IPortalAwareController;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.io.IOException;


public class PortalController {

    @FXML private BorderPane mainPortal;
    @FXML private HBox headerInclude;
    @FXML private VBox sidebarInclude;
    @FXML private StackPane contentPane;
    private Button toggleButton;

    private boolean sidebarVisible = true;


    @FXML
    public void initialize() {
        headerInclude = (HBox) mainPortal.getTop();
        contentPane = (StackPane) mainPortal.getCenter();

        // Set up toggle button in the header
        toggleButton = (Button) headerInclude.lookup("#toggleButton");
        toggleButton.setOnAction(e -> toggleSidebar());

        // Load the sidebar and set it up
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/Sidebar.fxml"));
            VBox sidebar = loader.load();

            SidebarController sidebarController = loader.getController();
            sidebarController.setPortalController(this); // Set the portal controller in the sidebar controller

            sidebarInclude.getChildren().setAll(sidebar.getChildren());
            sidebarInclude.setPrefWidth(220);
            sidebarInclude.setVisible(true);
        } catch (IOException e) {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("No se pudo cargar la barra lateral");
            alert.setContentText("Error al cargar la barra lateral: " + e.getMessage());
            alert.showAndWait();
        }
    }


    @FXML
    public void toggleSidebar() {
        if (sidebarVisible) {
            sidebarInclude.setPrefWidth(0);
            sidebarInclude.setVisible(false);
        } else {
            sidebarInclude.setPrefWidth(220);
            sidebarInclude.setVisible(true);
        }
        sidebarVisible = !sidebarVisible;
    }

    /**
     * This method loads the content of the specified FXML file into the content pane.
     * It uses the FXMLLoader to load the FXML file and sets the content of the Stack
     * <p>
     * I used StackPane to ensure that the content fills the available space.
     */
    public void loadContent(String fxmlPath) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource(fxmlPath));
            Parent content = loader.load();

            Object controller = loader.getController();
            if (controller instanceof IPortalAwareController) {
                ((IPortalAwareController) controller).setPortalController(this);
            }

            StackPane.setAlignment(content, javafx.geometry.Pos.CENTER);
            contentPane.getChildren().setAll(content);

        } catch (IOException e) {
            e.printStackTrace();
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error");
            alert.setHeaderText("No se pudo cargar el contenido");
            alert.setContentText("Error al cargar: " + fxmlPath + "\n" + e.getMessage());
            alert.showAndWait();
        }
    }

}
