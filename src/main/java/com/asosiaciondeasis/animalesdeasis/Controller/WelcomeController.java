package com.asosiaciondeasis.animalesdeasis.Controller;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class WelcomeController implements Initializable {

    private Stage stage;
    @FXML private AnchorPane mainContainer;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    // Receive the stage from the main application
    public void setStage(Stage stage) {
        this.stage = stage;
    }


    /**
     * This method is called when the "Continue" button is clicked.
     * It loads the PortalView.fxml and sets it as the current scene.
     */
    @FXML
    public void handleContinue(ActionEvent event) {
        try {
            boolean wasMaximized = stage.isMaximized();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PortalView.fxml"));
            Scene scene = new Scene(loader.load());

            stage.setMinWidth(1036);
            stage.setMinHeight(798);

            stage.setScene(scene);

            if (wasMaximized) {
                Platform.runLater(() -> {
                    stage.setMaximized(false);
                    Platform.runLater(() -> {
                        stage.setMaximized(true);
                    });
                });
            } else {
                stage.centerOnScreen();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
