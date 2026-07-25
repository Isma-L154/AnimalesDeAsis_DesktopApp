package com.asosiaciondeasis.animalesdeasis.Util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

/**
 * Small helper to check for real internet connectivity.
 *
 * <p>The previous implementation only resolved a DNS name, which returns
 * {@code true} even when the resolver is cached or a captive portal is present.
 * We instead attempt a short, bounded TCP connection to well-known hosts so a
 * missing network fails fast (bounded by {@link #TIMEOUT_MS}) instead of hanging
 * the caller.</p>
 */
public class NetworkUtils {

    private static final int TIMEOUT_MS = 1500;

    /** Hosts/ports tried in order; the first reachable one wins. */
    private static final String[][] PROBES = {
            {"8.8.8.8", "53"},          // Google DNS
            {"1.1.1.1", "53"},          // Cloudflare DNS
            {"firestore.googleapis.com", "443"}
    };

    private NetworkUtils() {
        // Utility class.
    }

    /**
     * @return {@code true} if any probe host is reachable within the timeout.
     */
    public static boolean isInternetAvailable() {
        for (String[] probe : PROBES) {
            if (canConnect(probe[0], Integer.parseInt(probe[1]))) {
                return true;
            }
        }
        return false;
    }

    private static boolean canConnect(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
