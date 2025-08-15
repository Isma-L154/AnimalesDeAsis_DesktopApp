package com.asosiaciondeasis.animalesdeasis.Config;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.security.MessageDigest;

public class CredentialsManager {
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";

    /**
     * Generates a key based on system properties and a fixed string
     * to ensure consistent encryption/decryption, (NOT SECURE FOR PRODUCTION USE).
     */
    private static String generateKey() {
        try {
            String systemInfo = System.getProperty("os.name") +
                    System.getProperty("user.name") +
                    "AnimalesDeAsis2024!";

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(systemInfo.getBytes());

            // We only need the first 16 bytes for AES-128
            byte[] keyBytes = new byte[16];
            System.arraycopy(hash, 0, keyBytes, 0, 16);

            return new String(keyBytes, "ISO-8859-1");
        } catch (Exception e) {
            return "AnimalesDeAsis16";
        }
    }

    /**
     * We don't use this method in production, but it is here for reference.
     * It encrypts the data using AES encryption with a generated key
     * */
    public static byte[] encrypt(byte[] data) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(generateKey().getBytes("ISO-8859-1"), ALGORITHM);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec);
        return cipher.doFinal(data);
    }


    public static byte[] decrypt(byte[] encryptedData) throws Exception {
        SecretKeySpec keySpec = new SecretKeySpec(generateKey().getBytes("ISO-8859-1"), ALGORITHM);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, keySpec);
        return cipher.doFinal(encryptedData);
    }

    public static InputStream getDecryptedCredentials() {
        try {
            InputStream encryptedStream = CredentialsManager.class.getResourceAsStream("/FireConfig/firebase-credentials.enc");

            if (encryptedStream == null) {
                System.out.println("⚠️ Firebase credentials file not found in resources");
                return null;
            }

            byte[] encryptedData = encryptedStream.readAllBytes();
            byte[] decryptedData = decrypt(encryptedData);

            return new java.io.ByteArrayInputStream(decryptedData);

        } catch (Exception e) {
            System.out.println("⚠️ Error loading/decrypting Firebase credentials: " + e.getMessage());
            return null;
        }
    }

}
