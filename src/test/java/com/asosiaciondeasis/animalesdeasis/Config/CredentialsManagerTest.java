package com.asosiaciondeasis.animalesdeasis.Config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class CredentialsManagerTest {

    private static final String PROP_KEY = "animalesdeasis.cred.key";

    @AfterEach
    void clearProperty() {
        System.clearProperty(PROP_KEY);
    }

    @Test
    void encryptThenDecryptReturnsOriginal() throws Exception {
        byte[] original = "{\"type\":\"service_account\"}".getBytes(StandardCharsets.UTF_8);

        byte[] encrypted = CredentialsManager.encrypt(original);
        byte[] decrypted = CredentialsManager.decrypt(encrypted);

        assertArrayEquals(original, decrypted);
    }

    @Test
    void encryptionUsesRandomIvSoCiphertextsDiffer() throws Exception {
        byte[] original = "same-plaintext-payload".getBytes(StandardCharsets.UTF_8);

        byte[] first = CredentialsManager.encrypt(original);
        byte[] second = CredentialsManager.encrypt(original);

        assertFalse(Arrays.equals(first, second),
                "Two encryptions of the same data must differ because the IV is random");
        // Both still decrypt back to the original.
        assertArrayEquals(original, CredentialsManager.decrypt(first));
        assertArrayEquals(original, CredentialsManager.decrypt(second));
    }

    @Test
    void decryptRejectsPayloadShorterThanIv() {
        assertThrows(IllegalArgumentException.class,
                () -> CredentialsManager.decrypt(new byte[8]));
    }

    /**
     * The wrong passphrase must never yield the original bytes.
     *
     * <p>This deliberately does <b>not</b> assert that decryption throws, which is
     * what it used to do. AES/CBC with PKCS#5 padding detects a wrong key only by
     * whether the trailing bytes happen to form valid padding, and random bytes
     * do so roughly once in 256 attempts. The assertion was therefore flaky by
     * construction - it failed on CI having passed locally, blocking an unrelated
     * change - and, worse, it was flaky about a security property, which is the
     * last place to accept a coin flip.</p>
     *
     * <p>What actually holds either way is that the plaintext does not come back.
     * That is asserted here.</p>
     *
     * <p>The underlying weakness is the cipher, not the test: CBC has no
     * integrity check, so a wrong key is indistinguishable from a corrupted or
     * tampered payload. Authenticated encryption - AES-GCM - detects both, every
     * time, and is where the credential work should end up.</p>
     */
    @Test
    void differentPassphraseCannotRecoverThePlaintext() throws Exception {
        byte[] original = "secret".getBytes(StandardCharsets.UTF_8);

        System.setProperty(PROP_KEY, "passphrase-A");
        byte[] encrypted = CredentialsManager.encrypt(original);

        System.setProperty(PROP_KEY, "passphrase-B");
        byte[] recovered = null;
        try {
            recovered = CredentialsManager.decrypt(encrypted);
        } catch (Exception expected) {
            // The usual outcome: padding does not validate under the wrong key.
            return;
        }

        assertFalse(Arrays.equals(original, recovered),
                "the wrong passphrase returned the original plaintext");
    }

    @Test
    void samePassphraseDecryptsAcrossCalls() throws Exception {
        byte[] original = "secret".getBytes(StandardCharsets.UTF_8);

        System.setProperty(PROP_KEY, "shared-passphrase");
        byte[] encrypted = CredentialsManager.encrypt(original);
        assertArrayEquals(original, CredentialsManager.decrypt(encrypted));
    }
}
