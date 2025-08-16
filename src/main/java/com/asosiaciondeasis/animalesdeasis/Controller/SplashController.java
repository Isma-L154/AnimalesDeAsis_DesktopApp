package com.asosiaciondeasis.animalesdeasis.Controller;

import com.asosiaciondeasis.animalesdeasis.AppInitializer;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

public class SplashController implements Initializable {

    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label statusLabel;

    private Stage stage;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        startInitialization();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    private void startInitialization() {
        Task<Void> initTask = new Task<Void>() {
            @Override
            protected Void call() throws Exception {
                Platform.runLater(() -> statusLabel.setText("Configurando base de datos..."));
                Thread.sleep(500);

                Platform.runLater(() -> statusLabel.setText("Conectando con Firebase..."));
                Thread.sleep(500);

                Platform.runLater(() -> statusLabel.setText("Sincronizando datos..."));
                AppInitializer.initializeApp();

                Platform.runLater(() -> statusLabel.setText("¡Listo!"));
                Thread.sleep(300);

                return null;
            }

            @Override
            protected void succeeded() {
                Platform.runLater(() -> loadWelcomeScreen());
            }

            @Override
            protected void failed() {
                Platform.runLater(() -> {
                    statusLabel.setText("Error al inicializar. Reintentando...");
                    progressIndicator.setVisible(false);
                });
            }
        };

        new Thread(initTask).start();
    }

    private void loadWelcomeScreen() {
        try {
            boolean wasMaximized = stage.isMaximized();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/WelcomeView.fxml"));
            Scene scene = new Scene(loader.load());

            WelcomeController controller = loader.getController();
            controller.setStage(stage);

            stage.setScene(scene);

            if (wasMaximized) {
                Platform.runLater(() -> {
                    stage.setMaximized(false);
                    Platform.runLater(() -> {
                        stage.setMaximized(true);
                    });
                });
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}