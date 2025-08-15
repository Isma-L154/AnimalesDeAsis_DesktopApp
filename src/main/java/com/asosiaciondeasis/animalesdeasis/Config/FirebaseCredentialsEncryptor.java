package com.asosiaciondeasis.animalesdeasis.Config;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

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

    // IMPORTANT: Update this path to point to your downloaded Firebase JSON file
    private static final String INPUT_FILE = "your-project-firebase-adminsdk.json";
    private static final String OUTPUT_FILE = "firebase-credentials.enc";

    /**
     * Generates an encryption key based on system properties.
     * NOTE: This key generation method is NOT secure for production use.
     * It's designed for development/demo purposes only.
     */

    private static String generateKey() {
        try {
            String systemInfo = System.getProperty("os.name") +
                    System.getProperty("user.name") +
                    "AnimalesDeAsis2024!";

            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(systemInfo.getBytes());

            // AES-128 requires exactly 16 bytes
            byte[] keyBytes = new byte[16];
            System.arraycopy(hash, 0, keyBytes, 0, 16);

            return new String(keyBytes, "ISO-8859-1");
        } catch (Exception e) {
            return "AnimalesDeAsis16"; // Fallback key
        }
    }

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

            // Encrypt the content
            SecretKeySpec keySpec = new SecretKeySpec(generateKey().getBytes("ISO-8859-1"), ALGORITHM);
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec);
            byte[] encryptedData = cipher.doFinal(fileContent);

            // Write encrypted data to the output file
            try (FileOutputStream fos = new FileOutputStream(outputFilePath)) {
                fos.write(encryptedData);
            }

            System.out.println("✅ Firebase credentials encrypted successfully!");
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

    /*
     * UNCOMMENT THE METHOD BELOW TO ENCRYPT YOUR FIREBASE CREDENTIALS
     *
     * HOW TO USE:
     * 1. Update INPUT_FILE constant with your Firebase JSON file path
     * 2. Uncomment the main() method below
     * 3. Run this class
     * 4. Follow the printed instructions
     * 5. Re-comment this method when done
     */
    
    /*
    public static void main(String[] args) {
        System.out.println("🔐 Firebase Credentials Encryptor");
        System.out.println("==================================");
        
        // Check if input file exists
        if (!Files.exists(Paths.get(INPUT_FILE))) {
            System.err.println("❌ Input file not found: " + INPUT_FILE);
            System.err.println("💡 Make sure to:");
            System.err.println("   1. Download your Firebase service account JSON");
            System.err.println("   2. Place it in the project root");
            System.err.println("   3. Update INPUT_FILE constant with the correct filename");
            return;
        }
        
        // Encrypt the credentials
        encryptCredentials(INPUT_FILE, OUTPUT_FILE);
    }
    */
}