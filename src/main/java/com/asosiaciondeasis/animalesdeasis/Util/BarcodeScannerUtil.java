package com.asosiaciondeasis.animalesdeasis.Util;

import com.github.sarxos.webcam.Webcam;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.*;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BarcodeScannerUtil {
    /**
     * This class is a placeholder for barcode scanning functionality.
     * Is implemented using libraries like ZXing or ZBar for Java.
     */

    private static final Logger LOGGER = Logger.getLogger(BarcodeScannerUtil.class.getName());
    private volatile boolean running = false;
    private Webcam webcam;
    private ExecutorService executor;

    /**
     * Starts the barcode scanning process using the default webcam.
     * The scanned code is returned through the provided callback.
     * <p>
     * This method manages UI, webcam access and scanning in a separate thread.
     * Resources are properly released and errors are handled.
     *
     * @param callback Callback to handle the scanned code.
     */

    public void startScanning(ScanCallback callback) {
        if (!initializeWebcam()) return;

        running = true;
        executor = Executors.newSingleThreadExecutor();

        Stage stage = createScannerStage();
        ImageView imageView = (ImageView) ((BorderPane) stage.getScene().getRoot()).getCenter();

        executor.submit(() -> scanLoop(callback, imageView, stage));
    }

    /**
     * Initializes the webcam device.
     *
     * @return true if successful, false otherwise.
     */
    private boolean initializeWebcam() {
        webcam = Webcam.getDefault();
        if (webcam == null) {
            showError("No se detectó ninguna cámara.");
            LOGGER.warning("No webcam detected.");
            return false;
        }
        webcam.setViewSize(new Dimension(640, 480));
        try {
            webcam.open();
        } catch (Exception e) {
            showError("No se pudo abrir la cámara.");
            LOGGER.log(Level.SEVERE, "Failed to open webcam", e);
            return false;
        }
        return true;
    }

    /**
     * Creates and setups the Camera using JavaFX.
     */
    private Stage createScannerStage() {
        ImageView imageView = new ImageView();
        imageView.setPreserveRatio(true);
        imageView.setFitWidth(640);

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.setOnAction(e -> stopScanning());

        BorderPane root = new BorderPane();
        root.setCenter(imageView);
        root.setBottom(cancelBtn);

        Stage stage = new Stage();
        stage.setTitle("Escaneo de código de barras");
        stage.setScene(new Scene(root));
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setOnCloseRequest(e -> stopScanning());
        Platform.runLater(stage::show);

        return stage;
    }

    /**
     * Scans the webcam for barcodes in a loop.
     * If a barcode is detected, it calls the callback and stops scanning.
     * If no barcode is detected, it continues scanning until stopped.
     *
     * @param callback  Callback to handle the scanned code.
     * @param imageView ImageView to display the webcam feed.
     * @param stage     Stage to show the scanner UI.
     */
    private void scanLoop(ScanCallback callback, ImageView imageView, Stage stage) {
        try {
            while (running) {
                BufferedImage image = webcam.getImage();
                if (image == null) continue;

                // Update the UI with the latest webcam image
                Platform.runLater(() -> {
                    WritableImage fxImage = SwingFXUtils.toFXImage(image, null);
                    imageView.setImage(fxImage);
                });

                String scannedCode = decodeBarcode(image);
                if (scannedCode != null) {
                    running = false;
                    Platform.runLater(() -> {
                        callback.onCodeScanned(scannedCode);
                        stage.close();
                    });
                    break;
                }
                Thread.sleep(200);
            }
        } catch (InterruptedException e) {
            LOGGER.log(Level.WARNING, "Scanning interrupted", e);
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unexpected error in scan loop", e);
        } finally {
            stopScanning();
            Platform.runLater(stage::close);
        }
    }

    /**
     * Decodes the barcode from the given image using ZXing library.
     * If no barcode is found, it returns null.
     *
     * @param image BufferedImage containing the webcam feed.
     * @return The decoded barcode text or null if not found.
     */
    private String decodeBarcode(BufferedImage image) {
        LuminanceSource source = new BufferedImageLuminanceSource(image);
        BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
        try {
            Result result = new MultiFormatReader().decode(bitmap);
            return result.getText();
        } catch (NotFoundException e) {
            return null;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error decoding barcode", e);
            return null;
        }
    }

    /**
     * Stops the scanning process and releases resources.
     */
    public synchronized void stopScanning() {
        running = false;
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }
        if (executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }

    /**
     * Shows an error dialog in the UI thread.
     */
    private void showError(String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle("Error de cámara");
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public interface ScanCallback {
        void onCodeScanned(String code);
    }
}
