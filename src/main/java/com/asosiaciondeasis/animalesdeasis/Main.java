package com.asosiaciondeasis.animalesdeasis;

import com.asosiaciondeasis.animalesdeasis.Controller.SplashController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Objects;

public class Main extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/SplashView.fxml"));
        Parent root = loader.load();

        SplashController controller = loader.getController();
        controller.setStage(stage);

        Scene scene = new Scene(root);
        stage.setTitle("Asociación de Asís - Cargando...");
        stage.setScene(scene);
        stage.setMaximized(true);
        stage.setResizable(true);

        stage.getIcons().add(new Image(Objects.requireNonNull(getClass().getResourceAsStream("/images/AdeAsisLogo.png"))));

        stage.centerOnScreen();
        stage.show();
    }
}