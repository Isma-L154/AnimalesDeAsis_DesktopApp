package com.asosiaciondeasis.animalesdeasis.Config;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.SecureRandom;

public class CredentialsManager {
    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding"; // Fixed: Use CBC mode
    private static final int IV_LENGTH = 16; // AES block size

    /**
     * Generates a key based on system properties and a fixed string
     * to ensure consistent encryption/decryption, (NOT SECURE FOR PRODUCTION USE).
     */
    private static byte[] generateKey() throws UnsupportedEncodingException {
        try {
            String systemInfo = System.getProperty("os.name") +
                    System.getProperty("user.name") +
                    "AnimalesDeAsis2024!";

            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(systemInfo.getBytes("UTF-8"));

            // We only need the first 16 bytes for AES-128
            byte[] keyBytes = new byte[16];
            System.arraycopy(hash, 0, keyBytes, 0, 16);

            return keyBytes; // Return a byte array directly
        } catch (Exception e) {
            return "AnimalesDeAsis16".getBytes("UTF-8"); // Fallback key as bytes
        }
    }

    /**
     * Encrypts data using AES/CBC with random IV
     */
    public static byte[] encrypt(byte[] data) throws Exception {
        byte[] keyBytes = generateKey();
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);

        // Generate random IV
        byte[] iv = new byte[IV_LENGTH];
        new SecureRandom().nextBytes(iv);
        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);

        byte[] encryptedData = cipher.doFinal(data);

        // Prepend IV to encrypted data
        byte[] result = new byte[IV_LENGTH + encryptedData.length];
        System.arraycopy(iv, 0, result, 0, IV_LENGTH);
        System.arraycopy(encryptedData, 0, result, IV_LENGTH, encryptedData.length);

        return result;
    }

    public static byte[] decrypt(byte[] encryptedDataWithIv) throws Exception {
        byte[] keyBytes = generateKey();
        SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);

        // Extract IV from the beginning
        byte[] iv = new byte[IV_LENGTH];
        byte[] encryptedData = new byte[encryptedDataWithIv.length - IV_LENGTH];

        System.arraycopy(encryptedDataWithIv, 0, iv, 0, IV_LENGTH);
        System.arraycopy(encryptedDataWithIv, IV_LENGTH, encryptedData, 0, encryptedData.length);

        IvParameterSpec ivSpec = new IvParameterSpec(iv);

        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);

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