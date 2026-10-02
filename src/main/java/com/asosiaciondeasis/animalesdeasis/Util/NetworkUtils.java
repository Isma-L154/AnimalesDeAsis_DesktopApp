package com.asosiaciondeasis.animalesdeasis.Util;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.List;

/**
 * Checks for real internet connectivity.
 *
 * <p>Resolving a DNS name is not enough: it succeeds from a cached resolver or
 * behind a captive portal. A short TCP connection to well-known hosts fails
 * fast, bounded by {@link #TIMEOUT_MS}, instead of hanging the caller.</p>
 */
public final class NetworkUtils {

    private static final int TIMEOUT_MS = 1500;

    private record Probe(String host, int port) {
    }

    /** Tried in order; the first reachable one wins. */
    private static final List<Probe> PROBES = List.of(
            new Probe("8.8.8.8", 53),          // Google DNS
            new Probe("1.1.1.1", 53),          // Cloudflare DNS
            new Probe("firestore.googleapis.com", 443));

    private NetworkUtils() {
    }

    /** @return {@code true} if any probe host is reachable within the timeout */
    public static boolean isInternetAvailable() {
        for (Probe probe : PROBES) {
            if (canConnect(probe.host(), probe.port())) {
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
