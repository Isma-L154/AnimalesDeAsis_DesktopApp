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

    @Test
    void differentPassphraseCannotDecrypt() throws Exception {
        byte[] original = "secret".getBytes(StandardCharsets.UTF_8);

        System.setProperty(PROP_KEY, "passphrase-A");
        byte[] encrypted = CredentialsManager.encrypt(original);

        System.setProperty(PROP_KEY, "passphrase-B");
        assertThrows(Exception.class, () -> CredentialsManager.decrypt(encrypted),
                "Decryption with a different passphrase must fail");
    }

    @Test
    void samePassphraseDecryptsAcrossCalls() throws Exception {
        byte[] original = "secret".getBytes(StandardCharsets.UTF_8);

        System.setProperty(PROP_KEY, "shared-passphrase");
        byte[] encrypted = CredentialsManager.encrypt(original);
        assertArrayEquals(original, CredentialsManager.decrypt(encrypted));
    }
}
