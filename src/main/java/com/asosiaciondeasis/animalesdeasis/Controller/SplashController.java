package com.asosiaciondeasis.animalesdeasis.Controller;

import com.asosiaciondeasis.animalesdeasis.AppInitializer;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

public class SplashController {
    private static final Logger log = LoggerFactory.getLogger(SplashController.class);

    @FXML private ProgressIndicator progressIndicator;
    @FXML private Label statusLabel;

    private Stage stage;

    @FXML
    public void initialize() {
        Task<Void> initTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                AppInitializer.initializeApp(this::updateMessage);
                return null;
            }
        };
        statusLabel.textProperty().bind(initTask.messageProperty());
        initTask.setOnSucceeded(e -> loadWelcomeScreen());
        initTask.setOnFailed(e -> {
            // This used to say "Reintentando..." while retrying nothing and
            // logging nothing, so a failed start left no trace anywhere.
            log.error("Application failed to initialise", initTask.getException());
            statusLabel.textProperty().unbind();
            statusLabel.setText("No se pudo iniciar la aplicación. Revise su conexión y vuelva a abrirla.");
            progressIndicator.setVisible(false);
        });

        Thread thread = new Thread(initTask, "app-init");
        // A stalled start must not keep the process alive once the window is closed.
        thread.setDaemon(true);
        thread.start();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    private void loadWelcomeScreen() {
        try {
            boolean wasMaximized = stage.isMaximized();

            FXMLLoader loader = new FXMLLoader(SplashController.class.getResource("/fxml/WelcomeView.fxml"));
            Scene scene = new Scene(loader.load());

            WelcomeController controller = loader.getController();
            controller.setStage(stage);

            stage.setScene(scene);

            if (wasMaximized) {
                Platform.runLater(() -> {
                    stage.setMaximized(false);
                    Platform.runLater(() -> stage.setMaximized(true));
                });
            }
        } catch (IOException e) {
            log.error("Could not load the welcome screen", e);
        }
    }
}
