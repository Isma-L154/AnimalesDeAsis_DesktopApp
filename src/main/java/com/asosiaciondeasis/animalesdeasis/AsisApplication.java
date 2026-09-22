package com.asosiaciondeasis.animalesdeasis;

import com.asosiaciondeasis.animalesdeasis.Controller.SplashController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Objects;

/** The JavaFX application itself. See {@link Main} for why it is not the entry point. */
public class AsisApplication extends Application {

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(
                AsisApplication.class.getResource("/fxml/SplashView.fxml"));
        Parent root = loader.load();

        SplashController controller = loader.getController();
        controller.setStage(stage);

        Scene scene = new Scene(root);
        stage.setTitle("Asociación de Asís - Sistema de Gestión");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.setResizable(true);

        stage.getIcons().add(new Image(Objects.requireNonNull(
                AsisApplication.class.getResourceAsStream("/images/AdeAsisLogo.png"))));

        stage.centerOnScreen();
        stage.show();
    }
}
