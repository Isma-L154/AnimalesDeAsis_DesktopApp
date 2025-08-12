package com.asosiaciondeasis.animalesdeasis.Util;

import com.asosiaciondeasis.animalesdeasis.Util.Helpers.NavigationHelper;
import com.github.sarxos.webcam.Webcam;
import com.google.zxing.*;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.common.HybridBinarizer;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.BorderPane;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BarcodeScannerUtil {

    private static final Logger LOGGER = Logger.getLogger(BarcodeScannerUtil.class.getName());
    private volatile boolean running = false;
    private Webcam webcam;
    private ExecutorService captureExecutor;
    private ExecutorService decodeExecutor;

    /**
     * Starts the barcode scanning process and opens the webcam dialog.
     * @param callback Callback to handle the scanned code.
     */
    public void startScanning(ScanCallback callback) {
        if (!initializeWebcam()) return;

        running = true;

        captureExecutor = Executors.newSingleThreadExecutor();
        decodeExecutor = Executors.newSingleThreadExecutor();

        Stage stage = createScannerStage();
        ImageView imageView = (ImageView) ((BorderPane) stage.getScene().getRoot()).getCenter();

        captureExecutor.submit(() -> {
            while (running) {
                try {
                    BufferedImage image = webcam.getImage();
                    if (image != null) {
                        Platform.runLater(() -> {
                            WritableImage fxImage = SwingFXUtils.toFXImage(image, null);
                            imageView.setImage(fxImage);
                        });

                        decodeExecutor.submit(() -> {
                            String scannedCode = decodeBarcode(image);
                            if (scannedCode != null && running) {
                                running = false;
                                Platform.runLater(() -> {
                                    callback.onCodeScanned(scannedCode);
                                    stage.close();
                                });
                            }
                        });
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error en captura", e);
                }
            }
        });
    }


    /**
     * Initializes the webcam and sets the best available resolution.
     * @return true if the webcam was initialized successfully, false otherwise.
     */
    private boolean initializeWebcam() {
        webcam = Webcam.getDefault();
        if (webcam == null) {
            NavigationHelper.showErrorAlert("Error", null,"No se detectó ninguna cámara." );
            LOGGER.warning("No webcam detected.");
            return false;
        }

        // CAMBIO: Seleccionar resolución más alta disponible
        Dimension bestRes = getMaxResolution(webcam.getViewSizes());
        LOGGER.info("Resolución seleccionada: " + bestRes.width + "x" + bestRes.height);
        webcam.setViewSize(bestRes);

        try {
            webcam.open();
        } catch (Exception e) {
            NavigationHelper.showErrorAlert("Error", null,"No se pudo abrir la camara." );
            LOGGER.log(Level.SEVERE, "Failed to open webcam", e);
            return false;
        }
        return true;
    }

    /**
     * Returns the maximum resolution from the supported resolutions.
     * @param supportedResolutions Array of supported resolutions.
     * @return The highest available resolution.
     */
    private Dimension getMaxResolution(Dimension[] supportedResolutions) {
        Dimension max = supportedResolutions[0];
        for (Dimension d : supportedResolutions) {
            if (d.width * d.height > max.width * max.height) {
                max = d;
            }
        }
        return max;
    }

    /**
     * Creates and configures the JavaFX stage for barcode scanning.
     * @return The configured Stage.
     */
    private Stage createScannerStage() {
        ImageView imageView = new ImageView();
        imageView.setPreserveRatio(true);

        // CAMBIO: Ajustar tamaño más grande
        imageView.setFitWidth(800);
        imageView.setFitHeight(600);
        BorderPane.setAlignment(imageView, Pos.CENTER);

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.setOnAction(e -> stopScanning());

        BorderPane root = new BorderPane();
        root.setCenter(imageView);
        root.setBottom(cancelBtn);

        Stage stage = new Stage();
        stage.setTitle("Escaneo de código de barras");
        stage.setScene(new Scene(root));
        stage.setMinWidth(800);
        stage.setMinHeight(600);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setOnCloseRequest(e -> stopScanning());
        Platform.runLater(stage::show);

        return stage;
    }

    /**
     * Attempts to decode a barcode from the given BufferedImage.
     * @param image The image to decode.
     * @return The decoded barcode as a String, or null if not found.
     */
    private String decodeBarcode(BufferedImage image) {
        try {
            LuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
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
     * Stops the scanning process, closes the webcam, and shuts down executors.
     */
    public synchronized void stopScanning() {
        running = false;
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }
        if (captureExecutor != null && !captureExecutor.isShutdown()) {
            captureExecutor.shutdownNow();
        }
        if (decodeExecutor != null && !decodeExecutor.isShutdown()) {
            decodeExecutor.shutdownNow();
        }
    }

    /**
     * Callback interface for handling scanned barcode results.
     */
    public interface ScanCallback { void onCodeScanned(String code);}
}
