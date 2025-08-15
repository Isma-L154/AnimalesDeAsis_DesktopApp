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


    @FXML
    public void handleContinue(ActionEvent event) {
        try {
            boolean wasMaximized = stage.isMaximized();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PortalView.fxml"));
            Scene scene = new Scene(loader.load());

            stage.setScene(scene);

            Platform.runLater(() -> {
                if (wasMaximized) {
                    stage.setMaximized(false);

                    Platform.runLater(() -> {
                        stage.setMinWidth(1036);
                        stage.setMinHeight(798);
                        stage.setMaximized(true);


                        Platform.runLater(() -> {
                            double width = stage.getWidth();
                            double height = stage.getHeight();

                            stage.setWidth(width - 1);
                            stage.setHeight(height - 1);

                            Platform.runLater(() -> {
                                stage.setWidth(width);
                                stage.setHeight(height);

                                // Forzar repaint final
                                scene.getRoot().requestLayout();
                                scene.getRoot().applyCss();
                            });
                        });
                    });
                } else {
                    stage.setMinWidth(1036);
                    stage.setMinHeight(798);
                    stage.centerOnScreen();
                }
            });

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
