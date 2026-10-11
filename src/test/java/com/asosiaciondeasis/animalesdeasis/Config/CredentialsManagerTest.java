package com.asosiaciondeasis.animalesdeasis.Config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;

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

    private static final Function<String, String> KNOWN =
            withPassphrase("una-passphrase-larga-y-aleatoria-para-pruebas");
    private static final Function<String, String> EMPTY_ENVIRONMENT = Map.<String, String>of()::get;

    /** A JVM cannot set its own environment, so the tests hand one in. */
    private static Function<String, String> withPassphrase(String passphrase) {
        return Map.of("ANIMALESDEASIS_CRED_KEY", passphrase)::get;
    }

    @Test
    @DisplayName("what goes in comes back out")
    void roundTrip() throws Exception {
        byte[] original = "{\"type\":\"service_account\"}".getBytes(StandardCharsets.UTF_8);

        byte[] recovered = CredentialsManager.decrypt(CredentialsManager.encrypt(original, KNOWN), KNOWN);

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

        byte[] first = CredentialsManager.encrypt(original, KNOWN);
        byte[] second = CredentialsManager.encrypt(original, KNOWN);

        assertFalse(Arrays.equals(first, second),
                "a repeated salt and nonce would undo the point of both");
        assertArrayEquals(original, CredentialsManager.decrypt(first, KNOWN));
        assertArrayEquals(original, CredentialsManager.decrypt(second, KNOWN));
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
        byte[] encrypted = CredentialsManager.encrypt(
                "secret".getBytes(StandardCharsets.UTF_8), withPassphrase("passphrase-A"));

        for (int attempt = 0; attempt < 50; attempt++) {
            CredentialsException e = assertThrows(CredentialsException.class,
                    () -> CredentialsManager.decrypt(encrypted, withPassphrase("passphrase-B")));
            assertEquals(CredentialsException.Reason.UNREADABLE, e.reason());
        }
    }

    @Test
    @DisplayName("altering a single byte is detected")
    void tamperingIsDetected() throws Exception {
        byte[] encrypted = CredentialsManager.encrypt("secret".getBytes(StandardCharsets.UTF_8), KNOWN);
        // Somewhere inside the ciphertext, past magic, version, salt and nonce.
        encrypted[encrypted.length - 3] ^= 0x01;

        CredentialsException e = assertThrows(CredentialsException.class,
                () -> CredentialsManager.decrypt(encrypted, KNOWN));
        assertEquals(CredentialsException.Reason.UNREADABLE, e.reason(),
                "CBC had no integrity check at all; this is what GCM buys");
    }

    @Test
    @DisplayName("the version byte is authenticated, not merely present")
    void headerIsCoveredByTheTag() throws Exception {
        byte[] encrypted = CredentialsManager.encrypt("secret".getBytes(StandardCharsets.UTF_8), KNOWN);
        encrypted[4] = 99;   // the version, immediately after the four magic bytes

        CredentialsException e = assertThrows(CredentialsException.class,
                () -> CredentialsManager.decrypt(encrypted, KNOWN));
        assertEquals(CredentialsException.Reason.LEGACY_FORMAT, e.reason());
    }

    /**
     * The central regression. Nothing may open a bundle without a passphrase
     * supplied from outside the code.
     */
    @Test
    @DisplayName("no passphrase means failure, never a built-in default")
    void thereIsNoFallbackKey() throws Exception {
        byte[] encrypted = CredentialsManager.encrypt("secret".getBytes(StandardCharsets.UTF_8), KNOWN);

        CredentialsException onDecrypt = assertThrows(CredentialsException.class,
                () -> CredentialsManager.decrypt(encrypted, EMPTY_ENVIRONMENT));
        assertEquals(CredentialsException.Reason.NO_PASSPHRASE, onDecrypt.reason());

        CredentialsException onEncrypt = assertThrows(CredentialsException.class,
                () -> CredentialsManager.encrypt("x".getBytes(StandardCharsets.UTF_8), EMPTY_ENVIRONMENT));
        assertEquals(CredentialsException.Reason.NO_PASSPHRASE, onEncrypt.reason());
    }

    /**
     * A system property travels on the command line, where the process list
     * shows it to every user of the machine. The environment is the only source.
     */
    @Test
    @DisplayName("a passphrase given as a system property is ignored")
    void systemPropertyIsNotASource() throws Exception {
        byte[] encrypted = CredentialsManager.encrypt("secret".getBytes(StandardCharsets.UTF_8), KNOWN);

        System.setProperty("animalesdeasis.cred.key", "una-passphrase-larga-y-aleatoria-para-pruebas");
        try {
            CredentialsException e = assertThrows(CredentialsException.class,
                    () -> CredentialsManager.decrypt(encrypted, EMPTY_ENVIRONMENT));
            assertEquals(CredentialsException.Reason.NO_PASSPHRASE, e.reason());
        } finally {
            System.clearProperty("animalesdeasis.cred.key");
        }
    }

    /**
     * Both constants are in this repository's git history, so anyone can try
     * them. They must open nothing.
     */
    @Test
    @DisplayName("the passphrases that used to be compiled in no longer work")
    void historicHardcodedKeysAreRejected() throws Exception {
        byte[] encrypted = CredentialsManager.encrypt(
                "secret".getBytes(StandardCharsets.UTF_8), withPassphrase("una-passphrase-real-y-larga"));

        for (String published : new String[]{
                "AnimalesDeAsis2024!FixedSecretKey",   // the CBC-era fallback
                "AnimalesDeAsis2024!",                 // the ECB-era key
                "AnimalesDeAsis16"}) {
            CredentialsException e = assertThrows(CredentialsException.class,
                    () -> CredentialsManager.decrypt(encrypted, withPassphrase(published)),
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

        byte[] encrypted = CredentialsManager.encrypt(original, KNOWN);

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
