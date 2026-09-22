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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Reads a barcode through the webcam, in a modal window with a live preview.
 *
 * <p>Two background threads keep the interface responsive: one captures frames
 * and one decodes them with ZXing.</p>
 */
public class BarcodeScannerUtil {

    private static final Logger log = LoggerFactory.getLogger(BarcodeScannerUtil.class);
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean callbackExecuted = new AtomicBoolean(false);
    /**
     * At most one frame is being decoded at a time. Capture produces a frame
     * every 50 ms and decoding a high-resolution frame takes longer, so queueing
     * every frame grew the decode queue - and the images it held - without bound.
     */
    private final AtomicBoolean decoding = new AtomicBoolean(false);
    private Webcam webcam;
    private ExecutorService captureExecutor;
    private ExecutorService decodeExecutor;
    private Stage currentStage;

    /**
     * Opens the scanning window and runs until a code is read, the user cancels,
     * or the camera fails.
     *
     * @param callback receives the decoded text, on the JavaFX application thread
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

                            if (decoding.compareAndSet(false, true)) {
                                decodeExecutor.submit(() -> decodeAndReport(image, callback));
                            }
                        }

                        // Small delay to prevent excessive CPU usage
                        Thread.sleep(50);

                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        log.error("Frame capture failed", e);
                    }
                }
            } finally {
                log.debug("Capture thread terminated");
            }
        });
    }

    private void decodeAndReport(BufferedImage image, ScanCallback callback) {
        try {
            String scannedCode = decodeBarcode(image);
            if (scannedCode == null || !running.get() || !callbackExecuted.compareAndSet(false, true)) {
                return;
            }
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
        } finally {
            decoding.set(false);
        }
    }

    private void resetState() {
        stopScanning();
        running.set(false);
        callbackExecuted.set(false);
        decoding.set(false);
    }

    /**
     * Opens the default webcam at its highest resolution, which decodes small
     * barcodes more reliably.
     *
     * @return whether the webcam is open; failures are reported to the user
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
                log.warn("No webcam detected");
                return false;
            }

            Dimension bestRes = getMaxResolution(webcam.getViewSizes());
            log.info("Webcam resolution {}x{}", bestRes.width, bestRes.height);
            webcam.setViewSize(bestRes);

            webcam.open();

            // Wait a moment for the camera to initialize
            Thread.sleep(500);

            return true;
        } catch (Exception e) {
            NavigationHelper.showErrorAlert("Error", null, "No se pudo abrir la cámara: " + e.getMessage());
            log.error("Failed to open webcam", e);
            return false;
        }
    }

    private Dimension getMaxResolution(Dimension[] supportedResolutions) {
        Dimension max = supportedResolutions[0];
        for (Dimension d : supportedResolutions) {
            if (d.width * d.height > max.width * max.height) {
                max = d;
            }
        }
        return max;
    }

    /** The modal preview window, with a cancel button. Closing it stops the scan. */
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

    /** @return the decoded text in any format ZXing supports, or {@code null} if none was found */
    private String decodeBarcode(BufferedImage image) {
        try {
            LuminanceSource source = new BufferedImageLuminanceSource(image);
            BinaryBitmap bitmap = new BinaryBitmap(new HybridBinarizer(source));
            Result result = new MultiFormatReader().decode(bitmap);
            return result.getText();
        } catch (NotFoundException e) {
            return null;
        } catch (Exception e) {
            log.warn("Error decoding barcode", e);
            return null;
        }
    }

    /** Stops capture and decoding and releases the camera. Safe to call when nothing is running. */
    public synchronized void stopScanning() {
        running.set(false);

        // Close webcam
        if (webcam != null && webcam.isOpen()) {
            try {
                webcam.close();
                log.debug("Webcam closed");
            } catch (Exception e) {
                log.warn("Error closing webcam", e);
            }
        }

        // Shutdown executors
        shutdownExecutor(captureExecutor, "CaptureExecutor");
        shutdownExecutor(decodeExecutor, "DecodeExecutor");

        captureExecutor = null;
        decodeExecutor = null;
    }

    /** Graceful shutdown with a bounded wait, then a forced one. */
    private void shutdownExecutor(ExecutorService executor, String name) {
        if (executor != null && !executor.isShutdown()) {
            try {
                executor.shutdown();
                if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                    log.warn("{} did not terminate gracefully, forcing shutdown", name);
                    executor.shutdownNow();
                    if (!executor.awaitTermination(2, TimeUnit.SECONDS)) {
                        log.error("{} did not terminate", name);
                    }
                }
            } catch (InterruptedException e) {
                log.warn("{} shutdown interrupted", name);
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }

    /** Receives a decoded code, on the JavaFX application thread. */
    @FunctionalInterface
    public interface ScanCallback {
        void onCodeScanned(String code);
    }
}