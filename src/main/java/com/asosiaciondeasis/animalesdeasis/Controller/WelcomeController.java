package com.asosiaciondeasis.animalesdeasis.Controller;

import com.asosiaciondeasis.animalesdeasis.Config.FirebaseConfig;
import com.asosiaciondeasis.animalesdeasis.Util.NetworkUtils;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.geometry.Insets;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.fxml.Initializable;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;
import java.util.ResourceBundle;

/**
 * Controller for the landing screen.
 *
 * <p>Besides the "start" action it now offers shortcuts that open a specific
 * section directly, and shows whether the app can sync right now. The
 * connectivity probe performs network I/O, so it runs on a background
 * {@link Task} — never on the JavaFX application thread.</p>
 */
public class WelcomeController implements Initializable {

    /**
     * Share of the window height taken by the artwork. Driving the image from the
     * height (rather than the width) keeps the dog the same visual size on any
     * monitor: binding to width made it swallow the screen when maximised.
     */
    private static final double ART_HEIGHT_RATIO = 0.42;

    private Stage stage;

    @FXML private StackPane mainContainer;
    @FXML private VBox heroBox;
    @FXML private ImageView dogImageView;
    @FXML private Label statusBadge;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        bindArtworkSize();
        checkConnectivityAsync();
    }

    /**
     * Scales the artwork with the window instead of using a hardcoded 900x600, so
     * the dog keeps peeking from the bottom edge at any size without overlapping
     * the content above it.
     *
     * <p>The hero's bottom padding is bound to the artwork height so the copy stays
     * optically centred in the free space above the dog, rather than leaving a gap
     * that grows on tall monitors.</p>
     */
    private void bindArtworkSize() {
        dogImageView.fitHeightProperty().bind(
                mainContainer.heightProperty().multiply(ART_HEIGHT_RATIO));
        dogImageView.setFitWidth(0); // width follows from preserveRatio

        heroBox.paddingProperty().bind(Bindings.createObjectBinding(
                () -> new Insets(32, 32, dogImageView.getFitHeight() * 0.80, 32),
                dogImageView.fitHeightProperty()));
    }

    /**
     * Probes connectivity off the UI thread and updates the badge when done.
     * Firebase may also be unavailable (missing credentials), in which case the
     * app is offline-only regardless of the network.
     */
    private void checkConnectivityAsync() {
        Task<Boolean> probe = new Task<>() {
            @Override
            protected Boolean call() {
                return FirebaseConfig.isFirebaseAvailable() && NetworkUtils.isInternetAvailable();
            }
        };

        probe.setOnSucceeded(e -> applyStatus(Boolean.TRUE.equals(probe.getValue())));
        probe.setOnFailed(e -> applyStatus(false));

        Thread thread = new Thread(probe, "welcome-connectivity-probe");
        thread.setDaemon(true); // must not keep the JVM alive on exit
        thread.start();
    }

    /** Updates the badge text/style. Called on the FX thread by the Task callbacks. */
    private void applyStatus(boolean online) {
        statusBadge.getStyleClass().removeAll("status-online", "status-offline");
        if (online) {
            statusBadge.setText("● En línea · los datos se sincronizan");
            statusBadge.getStyleClass().add("status-online");
        } else {
            statusBadge.setText("● Sin conexión · se guardará localmente");
            statusBadge.getStyleClass().add("status-offline");
        }
    }

    /** Receives the stage from the main application. */
    public void setStage(Stage stage) {
        this.stage = stage;
    }

    /** Opens the portal on its default (empty) content. */
    @FXML
    public void handleContinue(ActionEvent event) {
        openPortal(null);
    }

    /** Shortcut: opens the portal directly on the animals section. */
    @FXML
    public void handleGoToAnimals(ActionEvent event) {
        openPortal("/fxml/Animal/AnimalManagement.fxml");
    }

    /** Shortcut: opens the portal directly on the statistics section. */
    @FXML
    public void handleGoToStatistics(ActionEvent event) {
        openPortal("/fxml/Statistics/StatisticsManagement.fxml");
    }

    /**
     * Loads the portal and, when {@code initialContent} is provided, immediately
     * navigates to that section so the user skips a redundant click.
     *
     * @param initialContent FXML path to open on arrival, or {@code null} for none
     */
    private void openPortal(String initialContent) {
        try {
            boolean wasMaximized = stage.isMaximized();

            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/PortalView.fxml"));
            Scene scene = new Scene(loader.load());

            stage.setMinWidth(1036);
            stage.setMinHeight(798);
            stage.setScene(scene);

            if (initialContent != null) {
                PortalController portalController = loader.getController();
                // Deferred so the portal finishes its own initialize() first.
                Platform.runLater(() -> portalController.loadContent(initialContent));
            }

            if (wasMaximized) {
                Platform.runLater(() -> {
                    stage.setMaximized(false);
                    Platform.runLater(() -> stage.setMaximized(true));
                });
            } else {
                stage.centerOnScreen();
            }

        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
