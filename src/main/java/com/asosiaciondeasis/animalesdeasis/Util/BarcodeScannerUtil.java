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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

public class BarcodeScannerUtil {

    private static final Logger LOGGER = Logger.getLogger(BarcodeScannerUtil.class.getName());
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean callbackExecuted = new AtomicBoolean(false);
    private Webcam webcam;
    private ExecutorService captureExecutor;
    private ExecutorService decodeExecutor;
    private Stage currentStage;

    /**
     * Starts the barcode scanning process and opens the webcam dialog.
     * @param callback Callback to handle the scanned code.
     */
    public synchronized void startScanning(ScanCallback callback) {
        // Reset state for new scanning session
        resetState();

        if (!initializeWebcam()) {
            return;
        }

        running.set(true);
        callbackExecuted.set(false);

        captureExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "BarcodeCaptureThread");
            t.setDaemon(true);
            return t;
        });

        decodeExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "BarcodeDecodeThread");
            t.setDaemon(true);
            return t;
        });

        currentStage = createScannerStage();
        ImageView imageView = (ImageView) ((BorderPane) currentStage.getScene().getRoot()).getCenter();

        captureExecutor.submit(() -> {
            try {
                while (running.get() && !Thread.currentThread().isInterrupted()) {
                    try {
                        BufferedImage image = webcam.getImage();
                        if (image != null && running.get()) {
                            Platform.runLater(() -> {
                                if (running.get()) {
                                    WritableImage fxImage = SwingFXUtils.toFXImage(image, null);
                                    imageView.setImage(fxImage);
                                }
                            });

                            decodeExecutor.submit(() -> {
                                String scannedCode = decodeBarcode(image);
                                if (scannedCode != null && running.get() && callbackExecuted.compareAndSet(false, true)) {
                                    running.set(false);
                                    Platform.runLater(() -> {
                                        try {
                                            callback.onCodeScanned(scannedCode);
                                            NavigationHelper.showSuccessAlert("Escaneo Exitoso",
                                                    "Código escaneado correctamente: " + scannedCode);
                                            if (currentStage != null) {
                                                currentStage.close();
                                            }
                                        } finally {
                                            stopScanning();
                                        }
                                    });
                                }
                            });
                        }

                        // Small delay to prevent excessive CPU usage
                        Thread.sleep(50);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        LOGGER.log(Level.SEVERE, "Error en captura", e);
                    }
                }
            } finally {
                LOGGER.info("Capture thread terminated");
            }
        });
    }

    /**
     * Resets the internal state for a new scanning session
     */
    private void resetState() {
        stopScanning();
        running.set(false);
        callbackExecuted.set(false);
    }

    /**
     * Initializes the webcam and sets the best available resolution.
     * @return true if the webcam was initialized successfully, false otherwise.
     */
    private boolean initializeWebcam() {
        try {
            // Close any existing webcam
            if (webcam != null && webcam.isOpen()) {
                webcam.close();
                webcam = null;
            }

            webcam = Webcam.getDefault();
            if (webcam == null) {
                NavigationHelper.showErrorAlert("Error", null, "No se detectó ninguna cámara.");
                LOGGER.warning("No webcam detected.");
                return false;
            }

            Dimension bestRes = getMaxResolution(webcam.getViewSizes());
            LOGGER.info("Resolución seleccionada: " + bestRes.width + "x" + bestRes.height);
            webcam.setViewSize(bestRes);

            webcam.open();

            // Wait a moment for the camera to initialize
            Thread.sleep(500);

            return true;
        } catch (Exception e) {
            NavigationHelper.showErrorAlert("Error", null, "No se pudo abrir la cámara: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Failed to open webcam", e);
            return false;
        }
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
        imageView.setFitWidth(800);
        imageView.setFitHeight(600);
        BorderPane.setAlignment(imageView, Pos.CENTER);

        Button cancelBtn = new Button("Cancelar");
        cancelBtn.setOnAction(e -> {
            stopScanning();
            if (currentStage != null) {
                currentStage.close();
            }
        });

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
        running.set(false);

        // Close webcam
        if (webcam != null && webcam.isOpen()) {
            try {
                webcam.close();
                LOGGER.info("Webcam closed successfully");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error closing webcam", e);
            }
        }

        // Shutdown executors
        shutdownExecutor(captureExecutor, "CaptureExecutor");
        shutdownExecutor(decodeExecutor, "DecodeExecutor");

        captureExecutor = null;
        decodeExecutor = null;
    }

    /**
     * Properly shuts down an executor service
     */
    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor != null && !executor.isShutdown()) {
            try {
                executor.shutdown();
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    LOGGER.warning(name + " did not terminate gracefully, forcing shutdown");
                    executor.shutdownNow();
                    if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                        LOGGER.severe(name + " did not terminate");
                    }
                }
                LOGGER.info(name + " shutdown successfully");
            } catch (InterruptedException e) {
                LOGGER.warning(name + " shutdown interrupted");
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Callback interface for handling scanned barcode results.
     */
    public interface ScanCallback {
        void onCodeScanned(String code);
    }
}