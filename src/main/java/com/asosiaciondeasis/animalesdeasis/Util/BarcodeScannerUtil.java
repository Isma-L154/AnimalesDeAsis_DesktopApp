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

/**
 * BarcodeScannerUtil provides barcode scanning functionality using the computer's webcam.
 *
 * This utility class integrates webcam capture with ZXing barcode decoding library
 * to provide real-time barcode scanning capabilities in a JavaFX application.
 *
 * Features:
 * - Opens webcam in a modal dialog window
 * - Real-time camera preview with automatic barcode detection
 * - Supports multiple barcode formats through ZXing's MultiFormatReader
 * - Thread-safe operation with proper resource cleanup
 * - Graceful error handling and user feedback
 *
 * The scanning process runs on separate threads to avoid blocking the UI:
 * - Capture thread: Continuously captures frames from webcam
 * - Decode thread: Processes captured frames for barcode detection
 */
public class BarcodeScannerUtil {

    private static final Logger LOGGER = Logger.getLogger(BarcodeScannerUtil.class.getName());
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean callbackExecuted = new AtomicBoolean(false);
    private Webcam webcam;
    private ExecutorService captureExecutor;
    private ExecutorService decodeExecutor;
    private Stage currentStage;

    /**
     * Starts the barcode scanning process and opens a modal webcam dialog.
     *
     * This method:
     * 1. Resets any previous scanning state
     * 2. Initializes the webcam with optimal resolution
     * 3. Creates and displays a modal scanning window
     * 4. Starts background threads for camera capture and barcode decoding
     * 5. Executes the provided callback when a barcode is successfully scanned
     *
     * The scanning continues until either:
     * - A barcode is detected and decoded successfully
     * - The user cancels the operation
     * - An error occurs during the process
     *
     * @param callback Callback interface that handles the scanned barcode result
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
     * Resets the internal state for a new scanning session.
     *
     * This ensures that each scanning session starts with a clean state,
     * preventing interference from previous scanning attempts.
     * Called automatically before starting a new scan.
     */
    private void resetState() {
        stopScanning();
        running.set(false);
        callbackExecuted.set(false);
    }

    /**
     * Initializes the webcam and configures it with the best available resolution.
     *
     * Process:
     * 1. Closes any existing webcam connection
     * 2. Gets the default system webcam
     * 3. Determines the highest resolution supported by webcam
     * 4. Sets the webcam to use this optimal resolution
     * 5. Opens the webcam connection
     *
     * @return true if the webcam was successfully initialized, false if an error occurred
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
     * Determines the maximum resolution from an array of supported webcam resolutions.
     *
     * Compares the total pixel count (width × height) of each resolution
     * to find the one with the highest quality for better barcode detection accuracy.
     *
     * @param supportedResolutions Array of Dimension objects representing available resolutions
     * @return The Dimension object representing the highest available resolution
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
     * Creates and configures the JavaFX modal dialog for barcode scanning.
     *
     * The dialog includes:
     * - An ImageView for displaying the live camera feed
     * - A cancel button for user to abort the scanning process
     * - Proper window sizing and modality settings
     * - Event handlers for window close operations
     *
     * The stage is configured as APPLICATION_MODAL to prevent interaction
     * with other windows while scanning is in progress.
     *
     * @return The configured Stage ready to be displayed
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
     * Attempts to decode a barcode from the provided BufferedImage.
     *
     * Uses ZXing's MultiFormatReader, which supports various barcode formats including
     * - QR Code, Data Matrix, Aztec (2D formats)
     * - Code 128, Code 39, EAN-13, UPC-A (1D formats)
     * - And many other standard barcode formats
     *
     * The decoding process involves:
     * 1. Converting the BufferedImage to a LuminanceSource
     * 2. Creating a BinaryBitmap with hybrid binarization
     * 3. Using MultiFormatReader to attempt barcode detection
     *
     * @param image The BufferedImage captured from the webcam to analyze
     * @return The decoded barcode text as String, or null if no barcode was found
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
     * Stops the scanning process and performs cleanup of all resources.
     *
     * This method ensures proper resource management by:
     * 1. Setting the running flag too false to stop capture/decode loops
     * 2. Closing the webcam connection safely
     * 3. Shutting down executor services gracefully
     * 4. Clearing executor references
     *
     * Called automatically when:
     * - A barcode is successfully scanned
     * - The user cancels the operation
     * - The scanning window is closed
     * - An error occurs during scanning
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
     * Properly shuts down an ExecutorService with timeout handling.
     *
     * Follows the recommended shutdown pattern:
     * 1. Call shutdown() to stop accepting new tasks
     * 2. Wait for existing tasks to complete (with timeout)
     * 3. If tasks don't complete in time, force shutdown with shutdownNow()
     * 4. Handle InterruptedException appropriately
     *
     * This prevents resource leaks and ensures threads are properly terminated.
     *
     * @param executor The ExecutorService to shut down
     * @param name A descriptive name for logging purposes
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
     *
     * Implement this interface to define what should happen when a barcode
     * is successfully scanned and decoded. The callback is executed on the
     * JavaFX Application Thread, so it's safe to update UI components directly.
     *
     * Example usage:
     * scanner.startScanning(code -> {
     *     textField.setText(code);
     *     // Additional processing...
     * });
     */
    public interface ScanCallback {
        /**
         * Called when a barcode has been successfully scanned and decoded.
         *
         * @param code The decoded barcode content as a String
         */
        void onCodeScanned(String code);
    }
}