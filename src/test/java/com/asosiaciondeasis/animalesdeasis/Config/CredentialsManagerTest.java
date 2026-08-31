package com.asosiaciondeasis.animalesdeasis.Config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The credential scheme, after replacing a fallback constant that was public in a
 * public repository.
 *
 * <p>The tests that matter most here are the negative ones. The old failure mode
 * was not a crash — it was that everything appeared to work, because a missing
 * passphrase silently resolved to a compiled-in default. Several of these assert
 * that particular silence is gone.</p>
 */
class CredentialsManagerTest {

    private static final String PROP_KEY = "animalesdeasis.cred.key";

    @BeforeEach
    void useAKnownPassphrase() {
        System.setProperty(PROP_KEY, "una-passphrase-larga-y-aleatoria-para-pruebas");
    }

    @AfterEach
    void clearPassphrase() {
        System.clearProperty(PROP_KEY);
    }

    @Test
    @DisplayName("what goes in comes back out")
    void roundTrip() throws Exception {
        byte[] original = "{\"type\":\"service_account\"}".getBytes(StandardCharsets.UTF_8);

        byte[] recovered = CredentialsManager.decrypt(CredentialsManager.encrypt(original));

        assertArrayEquals(original, recovered);
    }

    /**
     * Reusing a nonce under the same key is the one mistake that breaks GCM
     * outright, so identical input must never produce identical output.
     */
    @Test
    @DisplayName("encrypting the same bytes twice gives different files")
    void saltAndNonceAreFreshEachTime() throws Exception {
        byte[] original = "secret".getBytes(StandardCharsets.UTF_8);

        byte[] first = CredentialsManager.encrypt(original);
        byte[] second = CredentialsManager.encrypt(original);

        assertFalse(Arrays.equals(first, second),
                "a repeated salt and nonce would undo the point of both");
        assertArrayEquals(original, CredentialsManager.decrypt(first));
        assertArrayEquals(original, CredentialsManager.decrypt(second));
    }

    /**
     * The whole reason for moving off CBC. Authentication makes this
     * deterministic, where the previous scheme detected a wrong key only by
     * whether random bytes happened to form valid padding — which they did 0.48%
     * of the time, making the equivalent test flaky.
     */
    @Test
    @DisplayName("a wrong passphrase always fails, and says why")
    void wrongPassphraseAlwaysFails() throws Exception {
        System.setProperty(PROP_KEY, "passphrase-A");
        byte[] encrypted = CredentialsManager.encrypt("secret".getBytes(StandardCharsets.UTF_8));

        System.setProperty(PROP_KEY, "passphrase-B");
        for (int attempt = 0; attempt < 50; attempt++) {
            CredentialsException e = assertThrows(CredentialsException.class,
                    () -> CredentialsManager.decrypt(encrypted));
            assertEquals(CredentialsException.Reason.UNREADABLE, e.reason());
        }
    }

    @Test
    @DisplayName("altering a single byte is detected")
    void tamperingIsDetected() throws Exception {
        byte[] encrypted = CredentialsManager.encrypt("secret".getBytes(StandardCharsets.UTF_8));
        // Somewhere inside the ciphertext, past magic, version, salt and nonce.
        encrypted[encrypted.length - 3] ^= 0x01;

        CredentialsException e = assertThrows(CredentialsException.class,
                () -> CredentialsManager.decrypt(encrypted));
        assertEquals(CredentialsException.Reason.UNREADABLE, e.reason(),
                "CBC had no integrity check at all; this is what GCM buys");
    }

    @Test
    @DisplayName("the version byte is authenticated, not merely present")
    void headerIsCoveredByTheTag() throws Exception {
        byte[] encrypted = CredentialsManager.encrypt("secret".getBytes(StandardCharsets.UTF_8));
        encrypted[4] = 99;   // the version, immediately after the four magic bytes

        CredentialsException e = assertThrows(CredentialsException.class,
                () -> CredentialsManager.decrypt(encrypted));
        assertEquals(CredentialsException.Reason.LEGACY_FORMAT, e.reason());
    }

    /**
     * The central regression. Nothing may open a bundle without a passphrase
     * supplied from outside the code.
     */
    @Test
    @DisplayName("no passphrase means failure, never a built-in default")
    void thereIsNoFallbackKey() throws Exception {
        byte[] encrypted = CredentialsManager.encrypt("secret".getBytes(StandardCharsets.UTF_8));

        System.clearProperty(PROP_KEY);

        CredentialsException onDecrypt = assertThrows(CredentialsException.class,
                () -> CredentialsManager.decrypt(encrypted));
        assertEquals(CredentialsException.Reason.NO_PASSPHRASE, onDecrypt.reason());

        CredentialsException onEncrypt = assertThrows(CredentialsException.class,
                () -> CredentialsManager.encrypt("x".getBytes(StandardCharsets.UTF_8)));
        assertEquals(CredentialsException.Reason.NO_PASSPHRASE, onEncrypt.reason());
    }

    /**
     * Both constants are in this repository's git history, so anyone can try
     * them. They must open nothing.
     */
    @Test
    @DisplayName("the passphrases that used to be compiled in no longer work")
    void historicHardcodedKeysAreRejected() throws Exception {
        System.setProperty(PROP_KEY, "una-passphrase-real-y-larga");
        byte[] encrypted = CredentialsManager.encrypt("secret".getBytes(StandardCharsets.UTF_8));

        for (String published : new String[]{
                "AnimalesDeAsis2024!FixedSecretKey",   // the CBC-era fallback
                "AnimalesDeAsis2024!",                 // the ECB-era key
                "AnimalesDeAsis16"}) {
            System.setProperty(PROP_KEY, published);
            CredentialsException e = assertThrows(CredentialsException.class,
                    () -> CredentialsManager.decrypt(encrypted),
                    published + " is in the public history and must open nothing");
            assertEquals(CredentialsException.Reason.UNREADABLE, e.reason());
        }
    }

    /**
     * An old bundle must be diagnosed rather than misread. Without the magic
     * bytes the reader would treat the first sixteen bytes as a salt and report
     * an unrelated failure.
     */
    @Test
    @DisplayName("a bundle in the previous format is named as such")
    void legacyBundlesAreIdentified() {
        byte[] legacyShaped = new byte[64];   // no ADAC magic
        Arrays.fill(legacyShaped, (byte) 7);

        CredentialsException e = assertThrows(CredentialsException.class,
                () -> CredentialsManager.decrypt(legacyShaped));
        assertEquals(CredentialsException.Reason.LEGACY_FORMAT, e.reason());
        assertTrue(e.getMessage().contains("SECURITY.md"),
                "the message has to say what to do about it");
    }

    @Test
    @DisplayName("a truncated file is reported as damaged")
    void shortPayloadIsRejected() {
        CredentialsException e = assertThrows(CredentialsException.class,
                () -> CredentialsManager.decrypt(new byte[8]));
        assertEquals(CredentialsException.Reason.UNREADABLE, e.reason());
    }

    @Test
    @DisplayName("a null payload does not throw NullPointerException")
    void nullPayloadIsRejectedCleanly() {
        CredentialsException e = assertThrows(CredentialsException.class,
                () -> CredentialsManager.decrypt(null));
        assertEquals(CredentialsException.Reason.UNREADABLE, e.reason());
    }

    @Test
    @DisplayName("the plaintext does not appear anywhere in the output")
    void ciphertextDoesNotLeakThePlaintext() throws Exception {
        byte[] original = "private_key_id_ABCDEFG".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = CredentialsManager.encrypt(original);

        assertNotEquals(-1, indexOf(encrypted, new byte[]{'A', 'D', 'A', 'C'}),
                "the magic header should be there");
        assertEquals(-1, indexOf(encrypted, original),
                "the plaintext must not survive into the bundle");
    }

    private static int indexOf(byte[] haystack, byte[] needle) {
        outer:
        for (int i = 0; i <= haystack.length - needle.length; i++) {
            for (int j = 0; j < needle.length; j++) {
                if (haystack[i + j] != needle[j]) {
                    continue outer;
                }
            }
            return i;
        }
        return -1;
    }
}
