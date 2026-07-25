package com.asosiaciondeasis.animalesdeasis.Config;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Handles symmetric encryption/decryption of the Firebase service-account bundle
 * ({@code firebase-credentials.enc}).
 *
 * <p><b>Why a passphrase instead of a hardcoded string?</b> The encrypted file
 * used to be readable by anyone who cloned the repository because the AES key
 * lived, in plaintext, a few lines above the ciphertext. The passphrase is now
 * resolved at runtime from the environment so the secret can live outside source
 * control (a CI secret, a machine-level environment variable, etc.) and be
 * rotated without recompiling.</p>
 *
 * <p>Resolution order for the passphrase:</p>
 * <ol>
 *   <li>Environment variable {@code ANIMALESDEASIS_CRED_KEY}</li>
 *   <li>JVM system property {@code animalesdeasis.cred.key}
 *       (e.g. {@code -Danimalesdeasis.cred.key=...})</li>
 *   <li>{@link #LEGACY_PASSPHRASE} — kept only so bundles produced before this
 *       change still decrypt. Distributing a build that relies on it is insecure;
 *       see {@code SECURITY.md} for the rotation procedure.</li>
 * </ol>
 *
 * <p>The scheme is AES-128 in CBC mode with a random 16-byte IV prepended to the
 * ciphertext. AES-128 (16-byte key) is retained for backward compatibility with
 * already-encrypted bundles.</p>
 */
public class CredentialsManager {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
    private static final int IV_LENGTH = 16;   // AES block size
    private static final int KEY_LENGTH = 16;  // AES-128

    private static final String ENV_KEY = "ANIMALESDEASIS_CRED_KEY";
    private static final String PROP_KEY = "animalesdeasis.cred.key";

    /**
     * Legacy passphrase used by bundles encrypted before the secret was moved out
     * of source control. Present only for backward compatibility — do not rely on
     * it for anything you distribute publicly.
     */
    private static final String LEGACY_PASSPHRASE = "AnimalesDeAsis2024!FixedSecretKey";

    private CredentialsManager() {
        // Utility class; not instantiable.
    }

    /**
     * Resolves the passphrase from the environment, falling back to the legacy
     * value so old bundles keep working.
     */
    private static String resolvePassphrase() {
        String fromEnv = System.getenv(ENV_KEY);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        String fromProp = System.getProperty(PROP_KEY);
        if (fromProp != null && !fromProp.isBlank()) {
            return fromProp;
        }
        return LEGACY_PASSPHRASE;
    }

    /**
     * Derives a 16-byte AES key from the resolved passphrase via SHA-256.
     * The intermediate hash is wiped after copying the key material.
     */
    private static byte[] generateKey() throws Exception {
        byte[] hash = null;
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            hash = md.digest(resolvePassphrase().getBytes(StandardCharsets.UTF_8));
            return Arrays.copyOf(hash, KEY_LENGTH);
        } finally {
            if (hash != null) Arrays.fill(hash, (byte) 0);
        }
    }

    /** Encrypts {@code data} with AES/CBC and a fresh random IV (prepended). */
    public static byte[] encrypt(byte[] data) throws Exception {
        byte[] keyBytes = generateKey();
        try {
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);

            byte[] iv = new byte[IV_LENGTH];
            new SecureRandom().nextBytes(iv);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            byte[] encryptedData = cipher.doFinal(data);

            byte[] result = new byte[IV_LENGTH + encryptedData.length];
            System.arraycopy(iv, 0, result, 0, IV_LENGTH);
            System.arraycopy(encryptedData, 0, result, IV_LENGTH, encryptedData.length);
            return result;
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    /** Decrypts data produced by {@link #encrypt(byte[])} (IV expected at the front). */
    public static byte[] decrypt(byte[] encryptedDataWithIv) throws Exception {
        if (encryptedDataWithIv == null || encryptedDataWithIv.length <= IV_LENGTH) {
            throw new IllegalArgumentException("Encrypted payload is too short to contain an IV");
        }
        byte[] keyBytes = generateKey();
        try {
            SecretKeySpec keySpec = new SecretKeySpec(keyBytes, ALGORITHM);

            byte[] iv = Arrays.copyOfRange(encryptedDataWithIv, 0, IV_LENGTH);
            byte[] encryptedData = Arrays.copyOfRange(encryptedDataWithIv, IV_LENGTH, encryptedDataWithIv.length);
            IvParameterSpec ivSpec = new IvParameterSpec(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, ivSpec);
            return cipher.doFinal(encryptedData);
        } finally {
            Arrays.fill(keyBytes, (byte) 0);
        }
    }

    /**
     * Loads and decrypts the bundled Firebase credentials.
     *
     * @return a stream over the decrypted JSON, or {@code null} if the bundle is
     *         missing or cannot be decrypted (the app then runs offline-only).
     */
    public static InputStream getDecryptedCredentials() {
        try (InputStream encryptedStream =
                     CredentialsManager.class.getResourceAsStream("/FireConfig/firebase-credentials.enc")) {

            if (encryptedStream == null) {
                System.out.println("⚠️ Firebase credentials file not found in resources");
                return null;
            }

            byte[] decryptedData = decrypt(encryptedStream.readAllBytes());
            return new java.io.ByteArrayInputStream(decryptedData);

        } catch (Exception e) {
            System.out.println("⚠️ Error loading/decrypting Firebase credentials: " + e.getMessage());
            return null;
        }
    }
}
