package com.asosiaciondeasis.animalesdeasis.Util;

import java.net.InetAddress;

//Class tio check if there's any internet connectivity
public class NetworkUtils {

    /**
     * Checks if the machine has internet access by trying to resolve a common domain (Google)
     * @return true if internet is available, false otherwise.
     */
    public static boolean isInternetAvailable() {
        try {
            InetAddress address = InetAddress.getByName("google.com");
            return !address.equals("");
        } catch (Exception e) {
            return false;
        }
    }
}
