package com.asosiaciondeasis.animalesdeasis.Config;

import java.io.FileOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * One-off tool that encrypts a Firebase service-account JSON into the bundle the
 * application ships.
 *
 * <h2>Usage</h2>
 * <pre>
 *   # Choose a long random passphrase and export it first. There is no default:
 *   # the tool refuses to run without one, on purpose.
 *   export ANIMALESDEASIS_CRED_KEY='...'                      # bash
 *   $env:ANIMALESDEASIS_CRED_KEY = '...'                      # PowerShell
 *
 *   mvn -q compile
 *   java -cp target/classes \
 *     com.asosiaciondeasis.animalesdeasis.Config.FirebaseCredentialsEncryptor \
 *     path/to/service-account.json
 * </pre>
 *
 * <p>Then delete the plaintext JSON. It is covered by {@code .gitignore}, but the
 * risk is the copy on disk, not the commit.</p>
 *
 * <h2>Encryption is not the whole answer</h2>
 *
 * <p>The bundle travels inside the installer and whatever opens it has to reach
 * the machine running the application, so a determined reader can always get the
 * credential back out. This raises the cost; it does not remove the exposure.
 * The Admin SDK also ignores Firestore security rules entirely, so the key it
 * protects grants unrestricted read and write over the whole database.</p>
 *
 * <p>{@code SECURITY.md} sets out what actually removes that, and the rotation
 * procedure that has to happen first.</p>
 */
public final class FirebaseCredentialsEncryptor {

    private static final String DEFAULT_OUTPUT =
            "src/main/resources/FireConfig/firebase-credentials.enc";

    private FirebaseCredentialsEncryptor() {
    }

    /**
     * @param inputPath  the downloaded service-account JSON
     * @param outputPath where to write the encrypted bundle
     */
    public static void encryptCredentials(String inputPath, String outputPath) throws Exception {
        byte[] plaintext = Files.readAllBytes(Paths.get(inputPath));
        byte[] encrypted = CredentialsManager.encrypt(plaintext);
        java.util.Arrays.fill(plaintext, (byte) 0);

        Path output = Paths.get(outputPath);
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }
        try (FileOutputStream out = new FileOutputStream(outputPath)) {
            out.write(encrypted);
        }
    }

    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Uso: FirebaseCredentialsEncryptor <input.json> [output.enc]");
            System.exit(2);
            return;
        }

        String input = args[0];
        String output = args.length >= 2 ? args[1] : DEFAULT_OUTPUT;

        if (!Files.exists(Paths.get(input))) {
            System.err.println("No existe el archivo de entrada: " + input);
            System.exit(2);
            return;
        }

        try {
            encryptCredentials(input, output);
        } catch (CredentialsException e) {
            // Almost always the missing passphrase. Previous versions carried on
            // with a built-in constant instead, which is how a public key ended
            // up protecting a live database.
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        } catch (Exception e) {
            System.err.println("No se pudo cifrar el archivo: " + e.getMessage());
            System.exit(1);
            return;
        }

        System.out.println("Bundle cifrado en: " + output);
        System.out.println();
        System.out.println("Ahora:");
        System.out.println("  1. Borrá el JSON en claro: " + input);
        System.out.println("  2. Actualizá el secreto FIREBASE_CREDENTIALS_ENC del repositorio:");
        System.out.println("       base64 -w0 " + output + "     (Linux)");
        System.out.println("       base64 -i  " + output + "     (macOS)");
        System.out.println("  3. Poné la misma passphrase en cada máquina que sincroniza,");
        System.out.println("     como variable de entorno ANIMALESDEASIS_CRED_KEY.");
        System.out.println();
        System.out.println("Sin el paso 3 la aplicación arranca en modo local: es lo esperado,");
        System.out.println("y es lo que antes quedaba tapado por la clave fija del código.");
    }
}
