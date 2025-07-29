package com.asosiaciondeasis.animalesdeasis.Controller;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.ImageView;
import javafx.scene.layout.AnchorPane; // Cambiado a AnchorPane
import javafx.stage.Stage;

import java.net.URL;
import java.util.ResourceBundle;

public class WelcomeController implements Initializable {

    @FXML
    private ImageView dogImageView;

    @FXML
    private AnchorPane mainContainer;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
    }

    @FXML
    private void handleScreenClick() {
        try {
            // Cargar la siguiente vista
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/NextView.fxml"));
            Parent root = loader.load();

            Scene scene = new Scene(root);
            Stage stage = (Stage) mainContainer.getScene().getWindow();
            stage.setScene(scene);

        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error al cambiar de vista: " + e.getMessage());
        }
    }

    // Método para recibir el Stage desde MainApplication
    public void setStage(Stage stage) {
    }
}
