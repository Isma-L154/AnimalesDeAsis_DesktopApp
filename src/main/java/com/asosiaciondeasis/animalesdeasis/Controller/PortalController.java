package com.asosiaciondeasis.animalesdeasis.Controller;


import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;


public class PortalController {

     @FXML private BorderPane mainPortal;
     @FXML private HBox headerInclude;
     @FXML private VBox sidebarInclude;
     private Button toggleButton;

    private boolean sidebarVisible = true;


    @FXML
    public void initialize() {
        headerInclude = (HBox) mainPortal.getTop();
        sidebarInclude = (VBox) mainPortal.getLeft();

        toggleButton = (Button) headerInclude.lookup("#toggleButton");
        toggleButton.setOnAction(e -> toggleSidebar());

        sidebarInclude.setPrefWidth(220);
        sidebarInclude.setVisible(true);
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
}
