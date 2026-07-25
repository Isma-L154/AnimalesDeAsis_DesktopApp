# Security Policy

## Firebase credentials

This desktop app talks to Cloud Firestore through the **Firebase Admin SDK**, which
requires a service-account key. That key grants full read/write access to the
Firestore database, so it must never be exposed.

### How credentials are handled

- The service-account JSON is **AES-encrypted** into `firebase-credentials.enc`.
- The decryption passphrase is resolved at runtime, **not** hardcoded in source:
  1. environment variable `ANIMALESDEASIS_CRED_KEY`
  2. JVM system property `-Danimalesdeasis.cred.key=...`
  3. a legacy fallback constant kept only so bundles encrypted before this change
     still open (do **not** ship a public build that depends on it).
- Neither the plaintext JSON nor `firebase-credentials.enc` is tracked in git
  (see `.gitignore`). The encrypted bundle is injected at build time from a CI
  secret.

> ⚠️ **Inherent limitation.** Because this is a desktop app, whatever key is used
> to decrypt the bundle must ultimately ship with the binary. Encryption raises
> the bar but does not make the credential unextractable from a distributed
> installer. The only way to fully remove client-side admin credentials is to put
> a backend (or Firebase client SDK + security rules) between the app and
> Firestore. That is out of scope here but recommended for a public release.

### If a key was ever committed — rotate it

1. **Firebase Console → Project Settings → Service accounts → Generate new private
   key.** Download the new JSON.
2. In **Service accounts → Manage service account permissions** (Google Cloud IAM),
   **delete/disable the old key** that leaked.
3. Re-encrypt the new JSON (see below) and update the CI secret.
4. Consider tightening Firestore security rules.

### Re-encrypting the bundle

1. Choose a strong passphrase and export it:
   ```bash
   export ANIMALESDEASIS_CRED_KEY='<long-random-passphrase>'
   ```
2. Point `FirebaseCredentialsEncryptor.INPUT_FILE` at the downloaded JSON,
   uncomment its `main()`, and run it. It writes `firebase-credentials.enc` using
   the same passphrase resolution as the app.
3. Copy the `.enc` into `src/main/resources/FireConfig/` **locally only**, delete
   the plaintext JSON, and re-comment `main()`.

### CI / release secrets

The GitHub Actions workflow needs two secrets to produce a working release:

| Secret                     | Contents                                              |
| -------------------------- | ----------------------------------------------------- |
| `FIREBASE_CREDENTIALS_ENC` | base64 of `firebase-credentials.enc`                  |
| `ANIMALESDEASIS_CRED_KEY`  | the passphrase used to encrypt that bundle            |

Create `FIREBASE_CREDENTIALS_ENC` with:
```bash
base64 -w0 src/main/resources/FireConfig/firebase-credentials.enc   # Linux
base64 -i  src/main/resources/FireConfig/firebase-credentials.enc   # macOS
```

## Reporting a vulnerability

Please open a private security advisory on the repository or email the maintainer
rather than filing a public issue.
