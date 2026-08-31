package com.asosiaciondeasis.animalesdeasis.Config;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * Encrypts and decrypts the Firebase service-account bundle
 * ({@code firebase-credentials.enc}).
 *
 * <h2>What was wrong with the previous version</h2>
 *
 * <p>It resolved the passphrase from the environment and, when nothing was set,
 * fell back <b>silently</b> to a constant compiled into this file — in a public
 * repository. Two such constants exist in this project's git history: an
 * AES/ECB-era key, and the one this class carried until now.</p>
 *
 * <p>That fallback was not theoretical. The build workflow injects the encrypted
 * bundle but never a passphrase, and the passphrase would have to be present on
 * each user's machine rather than on the build agent, so no distributed
 * installer ever had one. Every published build therefore decrypted with the
 * published constant. Anyone who downloaded an installer and read this file
 * could recover the service account — and the Admin SDK ignores Firestore
 * security rules, so that is unrestricted read and write over the whole
 * database.</p>
 *
 * <h2>What this version does</h2>
 *
 * <ul>
 *   <li><b>AES-256-GCM.</b> Authenticated, so a wrong key or an altered file is
 *       detected every time. The previous CBC mode had no integrity check at
 *       all: a wrong key produced garbage that only sometimes failed to unpad,
 *       which is why a test asserting it "must throw" turned out to be flaky
 *       0.48% of the time.</li>
 *   <li><b>PBKDF2 with a random salt</b> instead of a single SHA-256 pass. One
 *       hash of a passphrase is cheap to attack with a wordlist and, being
 *       unsalted, is attackable once against every bundle ever produced.</li>
 *   <li><b>No fallback.</b> A missing passphrase is an error that says so.
 *       Silence is what let a public constant protect a live database for a
 *       year.</li>
 * </ul>
 *
 * <h2>The limit this does not remove</h2>
 *
 * <p>This is a desktop application, so whatever opens the bundle must reach the
 * machine running it. Encryption raises the cost of extraction; it cannot make a
 * credential unextractable from something you hand people. The only real fix is
 * to stop shipping admin credentials — see {@code SECURITY.md}.</p>
 */
public final class CredentialsManager {

    /** Marks a bundle as this format, so an older one is diagnosed instead of misread. */
    private static final byte[] MAGIC = {'A', 'D', 'A', 'C'};
    private static final byte FORMAT_VERSION = 2;

    private static final String KDF = "PBKDF2WithHmacSHA256";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";

    private static final int SALT_BYTES = 16;
    private static final int IV_BYTES = 12;      // GCM's standard nonce length
    private static final int TAG_BITS = 128;
    private static final int KEY_BITS = 256;

    /**
     * Deliberately expensive. OWASP's guidance for PBKDF2-HMAC-SHA256 is
     * 600,000; this sits below that because it runs once at startup on modest
     * hardware in a shelter office, and the passphrase is a long random string
     * rather than something a person chose. It is around four hundred thousand
     * times more work per guess than the single SHA-256 it replaces.
     */
    private static final int ITERATIONS = 210_000;

    private static final String ENV_KEY = "ANIMALESDEASIS_CRED_KEY";
    private static final String PROP_KEY = "animalesdeasis.cred.key";
    private static final String BUNDLE_PATH = "/FireConfig/firebase-credentials.enc";

    private static final SecureRandom RANDOM = new SecureRandom();

    private CredentialsManager() {
    }

    /**
     * @throws CredentialsException with {@link CredentialsException.Reason#NO_PASSPHRASE}
     *         when neither source is set — never a built-in default
     */
    private static char[] resolvePassphrase() throws CredentialsException {
        String fromEnv = System.getenv(ENV_KEY);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv.toCharArray();
        }
        String fromProp = System.getProperty(PROP_KEY);
        if (fromProp != null && !fromProp.isBlank()) {
            return fromProp.toCharArray();
        }
        throw new CredentialsException(CredentialsException.Reason.NO_PASSPHRASE,
                "No hay passphrase para descifrar las credenciales de Firebase. "
                        + "Definí la variable de entorno " + ENV_KEY + " o la propiedad -D" + PROP_KEY
                        + ". La aplicación seguirá funcionando en modo local. Ver SECURITY.md.");
    }

    /**
     * Derives the key, then wipes the passphrase and the intermediate material.
     *
     * <p>Wiping is best-effort: {@code String} passphrases from the environment
     * stay in the heap until collected, which is why this works in
     * {@code char[]} from the point it can.</p>
     */
    private static SecretKey deriveKey(char[] passphrase, byte[] salt) throws Exception {
        PBEKeySpec spec = new PBEKeySpec(passphrase, salt, ITERATIONS, KEY_BITS);
        try {
            byte[] keyBytes = SecretKeyFactory.getInstance(KDF).generateSecret(spec).getEncoded();
            try {
                return new SecretKeySpec(keyBytes, "AES");
            } finally {
                Arrays.fill(keyBytes, (byte) 0);
            }
        } finally {
            spec.clearPassword();
            Arrays.fill(passphrase, '\0');
        }
    }

    /**
     * Encrypts {@code data}, producing
     * {@code MAGIC | version | salt | iv | ciphertext+tag}.
     *
     * <p>A fresh salt and nonce every time: reusing a GCM nonce under the same
     * key is the one mistake that breaks it outright.</p>
     */
    public static byte[] encrypt(byte[] data) throws Exception {
        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        RANDOM.nextBytes(salt);
        RANDOM.nextBytes(iv);

        SecretKey key = deriveKey(resolvePassphrase(), salt);
        Cipher cipher = Cipher.getInstance(TRANSFORMATION);
        cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));

        // The header is authenticated too, so the version cannot be edited to
        // talk the reader into a different interpretation.
        byte[] header = header();
        cipher.updateAAD(header);
        byte[] sealed = cipher.doFinal(data);

        return ByteBuffer.allocate(header.length + salt.length + iv.length + sealed.length)
                .put(header).put(salt).put(iv).put(sealed)
                .array();
    }

    private static byte[] header() {
        return ByteBuffer.allocate(MAGIC.length + 1).put(MAGIC).put(FORMAT_VERSION).array();
    }

    /** Decrypts what {@link #encrypt(byte[])} produced. */
    public static byte[] decrypt(byte[] payload) throws Exception {
        int headerLength = MAGIC.length + 1;
        int minimum = headerLength + SALT_BYTES + IV_BYTES + 1;

        if (payload == null || payload.length < minimum) {
            throw new CredentialsException(CredentialsException.Reason.UNREADABLE,
                    "El archivo de credenciales está incompleto o dañado.");
        }
        if (!Arrays.equals(Arrays.copyOf(payload, MAGIC.length), MAGIC)) {
            // No magic means a bundle from before this format. Saying so is the
            // whole point: the previous code would have tried to decrypt it and
            // reported an unrelated failure.
            throw new CredentialsException(CredentialsException.Reason.LEGACY_FORMAT,
                    "El bundle de credenciales usa el formato anterior (AES-CBC con clave derivada "
                            + "sin sal). Hay que volver a cifrarlo con FirebaseCredentialsEncryptor "
                            + "y rotar la service account, porque la clave anterior es pública. "
                            + "Ver SECURITY.md.");
        }
        if (payload[MAGIC.length] != FORMAT_VERSION) {
            throw new CredentialsException(CredentialsException.Reason.LEGACY_FORMAT,
                    "El bundle de credenciales tiene la versión de formato "
                            + payload[MAGIC.length] + " y esta versión de la aplicación espera "
                            + FORMAT_VERSION + ". Hay que volver a cifrarlo.");
        }

        ByteBuffer buffer = ByteBuffer.wrap(payload);
        byte[] header = new byte[headerLength];
        byte[] salt = new byte[SALT_BYTES];
        byte[] iv = new byte[IV_BYTES];
        buffer.get(header).get(salt).get(iv);
        byte[] sealed = new byte[buffer.remaining()];
        buffer.get(sealed);

        SecretKey key = deriveKey(resolvePassphrase(), salt);
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(header);
            return cipher.doFinal(sealed);
        } catch (javax.crypto.AEADBadTagException e) {
            // GCM tells these apart from a corrupt read, which CBC could not.
            throw new CredentialsException(CredentialsException.Reason.UNREADABLE,
                    "La passphrase no corresponde a este bundle, o el archivo fue alterado.", e);
        }
    }

    /**
     * Opens the bundled credentials.
     *
     * @return a stream over the decrypted JSON
     * @throws CredentialsException saying which reason it failed for, rather than
     *         returning {@code null} the way this used to — the caller has to be
     *         able to tell "no credentials configured" from "configured and
     *         broken", because only one of those is somebody's mistake
     */
    public static InputStream getDecryptedCredentials() throws CredentialsException {
        byte[] encrypted;
        try (InputStream stream = CredentialsManager.class.getResourceAsStream(BUNDLE_PATH)) {
            if (stream == null) {
                throw new CredentialsException(CredentialsException.Reason.MISSING_BUNDLE,
                        "No hay bundle de credenciales de Firebase. La aplicación funciona "
                                + "en modo local, sin sincronización.");
            }
            encrypted = stream.readAllBytes();
        } catch (CredentialsException e) {
            throw e;
        } catch (Exception e) {
            throw new CredentialsException(CredentialsException.Reason.UNREADABLE,
                    "No se pudo leer el archivo de credenciales.", e);
        }

        try {
            return new java.io.ByteArrayInputStream(decrypt(encrypted));
        } catch (CredentialsException e) {
            throw e;
        } catch (Exception e) {
            throw new CredentialsException(CredentialsException.Reason.UNREADABLE,
                    "No se pudieron descifrar las credenciales de Firebase.", e);
        }
    }
}
