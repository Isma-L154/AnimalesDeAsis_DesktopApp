package com.asosiaciondeasis.animalesdeasis.Config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteBatch;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Boots the Firebase Admin SDK far enough to prove its classpath is intact.
 *
 * <p>The SDK drags in gRPC, gax and protobuf transitively, and those break at *class-load* time,
 * not compile time: a protobuf major bump or a shaded-netty clash surfaces as a
 * NoClassDefFoundError the first time a batch is serialised, long after CI has gone green. The rest
 * of the suite never touches the real SDK -- it works against test doubles -- so nothing else would
 * catch that.
 *
 * <p>Deliberately offline: credentials are a syntactically valid but fake authorized_user, and the
 * batch is built and thrown away rather than committed, so no request ever leaves the machine.
 */
class FirebaseSdkBootstrapTest {

    private static final String APP_NAME = "sdk-bootstrap-test";

    /** A well-formed authorized_user credential. Nothing here is a real secret. */
    private static final String FAKE_CREDENTIALS = """
            {
              "type": "authorized_user",
              "client_id": "000000000000-testclientid.apps.googleusercontent.com",
              "client_secret": "not-a-real-secret",
              "refresh_token": "not-a-real-refresh-token"
            }
            """;

    @AfterEach
    void tearDown() {
        FirebaseApp.getApps().stream()
                .filter(app -> APP_NAME.equals(app.getName()))
                .forEach(FirebaseApp::delete);
    }

    @Test
    void buildsABatchedWriteWithoutTouchingTheNetwork() throws Exception {
        GoogleCredentials credentials = GoogleCredentials.fromStream(
                new ByteArrayInputStream(FAKE_CREDENTIALS.getBytes(StandardCharsets.UTF_8)));

        FirebaseOptions options = FirebaseOptions.builder()
                .setCredentials(credentials)
                .setProjectId("asis-test-project")
                .build();
        FirebaseApp app = FirebaseApp.initializeApp(options, APP_NAME);

        Firestore db = FirestoreClient.getFirestore(app);
        assertNotNull(db);

        // Mirrors how SyncService pushes: a collection reference, a batch, a set and a delete.
        // The set is what forces the protobuf serialisation path to load.
        CollectionReference animals = db.collection("animals");
        DocumentReference doc = animals.document("A-2026-0042");
        WriteBatch batch = db.batch();
        batch.set(doc, Map.of("recordNumber", "A-2026-0042", "synced", true));
        batch.delete(animals.document("A-2026-0001"));

        assertEquals("animals/A-2026-0042", doc.getPath());
        assertNotNull(batch);
    }
}
