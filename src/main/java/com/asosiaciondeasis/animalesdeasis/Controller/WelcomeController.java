package com.asosiaciondeasis.animalesdeasis.Controller;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class WelcomeController implements Initializable {

    private Stage stage;
    @FXML
    private AnchorPane mainContainer;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    // Receive the stage from the main application
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    //TODO fix the maximize issue in this method
    @FXML
    public void handleContinue(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PortalView.fxml"));
            Scene scene = new Scene(loader.load());
            stage.setScene(scene);
            stage.setMinWidth(1036);
            stage.setMinHeight(798);
            javafx.application.Platform.runLater(() -> {
                stage.setMaximized(true);
                stage.centerOnScreen();
            });
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
