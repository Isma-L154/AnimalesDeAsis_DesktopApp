package com.asosiaciondeasis.animalesdeasis;

import com.asosiaciondeasis.animalesdeasis.Controller.SplashController;
import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import java.util.Objects;

/**
 * The JavaFX application itself.
 *
 * <p>Separate from {@link Main} on purpose. The JavaFX launcher refuses to start
 * when the class holding {@code main} extends {@code Application} and the JavaFX
 * modules arrive on the classpath rather than the module path — which is exactly
 * how an IDE runs a class:</p>
 *
 * <pre>
 * Error: JavaFX runtime components are missing, and are required to run this
 * application
 * </pre>
 *
 * <p>Keeping the entry point in a class that does not extend {@code Application}
 * sidesteps that check entirely, so pressing Run in VS Code, IntelliJ or Eclipse
 * works with no launch configuration at all.</p>
 */
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
