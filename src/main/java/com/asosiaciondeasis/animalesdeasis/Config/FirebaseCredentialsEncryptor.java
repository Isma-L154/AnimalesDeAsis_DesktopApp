package com.asosiaciondeasis.animalesdeasis.Config;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.SecureRandom;

/**
 * FIREBASE CREDENTIALS ENCRYPTION UTILITY
 *
 * This class is provided as a reference for users who want to use Firebase functionality.
 * Follow these steps to set up Firebase credentials:
 *
 * STEP 1: Get your Firebase service account key
 *   - Go to Firebase Console → Project Settings → Service Accounts
 *   - Click "Generate new private key" 
 *   - Download the JSON file (e.g., "your-project-firebase-adminsdk.json")
 *
 * STEP 2: Encrypt your credentials
 *   - Place your Firebase JSON file in your project root
 *   - Uncomment the main() method below
 *   - Update the INPUT_FILE path to point to your JSON file
 *   - Run this class to generate the encrypted file
 *
 * STEP 3: Add the encrypted file to resources
 *   - Copy the generated "firebase-credentials.enc" file 
 *   - Place it in "src/main/resources/FireConfig/" directory
 *
 * STEP 4: Clean up
 *   - Delete the original JSON file from your project
 *   - Re-comment the main() method to avoid accidental execution
 *   - Add "*.json" to your .gitignore to prevent credential leaks
 *
 * SECURITY NOTE: 
 * This encryption method provides basic obfuscation only. For production
 * environments, consider using proper key management solutions like:
 * - Environment variables
 * - Azure Key Vault / AWS Secrets Manager / Google Secret Manager
 * - Hardware Security Modules (HSM)
 */
public class FirebaseCredentialsEncryptor {

    private static final String ALGORITHM = "AES";
    private static final String TRANSFORMATION = "AES/ECB/PKCS5Padding";
    private static final int IV_LENGTH = 16;

    // Default location of the encrypted bundle inside the project resources.
    private static final String DEFAULT_OUTPUT_FILE =
            "src/main/resources/FireConfig/firebase-credentials.enc";

    /**
     * Generates an encryption key based on system properties.
     * NOTE: This key generation method is NOT secure for production use.
     * It's designed for development/demo purposes only.
     */
    /**
     * Encrypts the Firebase credentials JSON file.
     *
     * @param inputFilePath Path to your Firebase service account JSON file
     * @param outputFilePath Path where the encrypted file will be saved
     */
    public static void encryptCredentials(String inputFilePath, String outputFilePath) {
        try {
            // Read the original JSON file
            byte[] fileContent = Files.readAllBytes(Paths.get(inputFilePath));

            // Use CredentialsManager's encrypt method (no duplication!)
            byte[] encryptedData = CredentialsManager.encrypt(fileContent);

            // Write encrypted data to the output file
            try (FileOutputStream fos = new FileOutputStream(outputFilePath)) {
                fos.write(encryptedData);
            }

            System.out.println("✅ Firebase credentials encrypted successfully with CBC mode!");
            System.out.println("📁 Encrypted file saved as: " + outputFilePath);
            System.out.println("📋 Next steps:");
            System.out.println("   1. Copy '" + outputFilePath + "' to 'src/main/resources/FireConfig/'");
            System.out.println("   2. Delete the original JSON file: '" + inputFilePath + "'");
            System.out.println("   3. Add '*.json' to your .gitignore file");
            System.out.println("   4. Re-comment the main() method in this class");

        } catch (Exception e) {
            System.err.println("❌ Error encrypting credentials: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * One-off tool to (re-)encrypt a Firebase service-account JSON.
     *
     * <p><b>Usage</b> (from IntelliJ: right-click → Run, or via Maven):</p>
     * <pre>
     *   args[0] = path to the downloaded service-account JSON  (required)
     *   args[1] = output path for the .enc file                (optional,
     *             defaults to src/main/resources/FireConfig/firebase-credentials.enc)
     * </pre>
     *
     * <p>Set the passphrase first so the bundle is encrypted with the same key the
     * app will use to decrypt it:</p>
     * <pre>
     *   Windows (PowerShell):  $env:ANIMALESDEASIS_CRED_KEY = "your-long-passphrase"
     *   or pass a JVM option:  -Danimalesdeasis.cred.key=your-long-passphrase
     * </pre>
     *
     * After it runs: delete the plaintext JSON and never commit it (it is already
     * covered by .gitignore).
     */
    public static void main(String[] args) {
        System.out.println("🔐 Firebase Credentials Encryptor");
        System.out.println("==================================");

        if (args.length < 1) {
            System.err.println("❌ Missing argument.");
            System.err.println("   Usage: FirebaseCredentialsEncryptor <input.json> [output.enc]");
            return;
        }

        String inputFile = args[0];
        String outputFile = args.length >= 2 ? args[1] : DEFAULT_OUTPUT_FILE;

        if (!Files.exists(Paths.get(inputFile))) {
            System.err.println("❌ Input file not found: " + inputFile);
            return;
        }

        boolean usingLegacyKey = System.getenv("ANIMALESDEASIS_CRED_KEY") == null
                && System.getProperty("animalesdeasis.cred.key") == null;
        if (usingLegacyKey) {
            System.out.println("⚠️  No passphrase set — encrypting with the LEGACY key.");
            System.out.println("    Set ANIMALESDEASIS_CRED_KEY (env) or -Danimalesdeasis.cred.key first to rotate.");
        }

        encryptCredentials(inputFile, outputFile);
    }
}