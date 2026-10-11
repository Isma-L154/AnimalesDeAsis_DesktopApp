package com.asosiaciondeasis.animalesdeasis.Util;

import com.github.eduramiba.webcamcapture.drivers.NativeDriver;
import com.github.sarxos.webcam.Webcam;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The scanner depends on two things that fail only when someone presses Scan:
 * the native driver being installed, and BridJ being absent.
 *
 * <p>Neither test opens a camera, so both run on a machine without one.</p>
 */
class BarcodeScannerDriverTest {

    @Test
    void theScannerInstallsTheNativeDriver() {
        new BarcodeScannerUtil();

        assertInstanceOf(NativeDriver.class, Webcam.getDriver());
    }

    /**
     * If BridJ came back, a missing {@code setDriver} call would go unnoticed:
     * webcam-capture would quietly use its own abandoned driver again.
     */
    @Test
    void theAbandonedNativeLayerIsNotOnTheClasspath() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("org.bridj.BridJ"));
    }
}
