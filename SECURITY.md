# Security Policy

## Reporting a vulnerability

Open a private security advisory on the repository, or email the maintainer.
Please do not file a public issue.

---

## Firebase credentials

This application talks to Cloud Firestore through the **Firebase Admin SDK**,
which needs a service-account key. The Admin SDK **bypasses Firestore security
rules entirely**, so that key grants unrestricted read and write over the whole
database — including deleting it.

### ⚠️ The existing key must be treated as public

Every service-account key ever shipped with this application should be assumed
compromised. This is not a precaution; it follows from what is in the repository:

- Two decryption keys have existed as constants in source, and both remain in the
  public git history: an `AES/ECB` key from the original scheme, and the
  `AES/CBC` fallback that replaced it.
- The fallback was **silent**. When no passphrase was configured, the application
  used the constant without saying anything.
- No passphrase was ever configured for a distributed build. The release workflow
  injects the encrypted bundle but no passphrase, and a passphrase would in any
  case have to be present on each user's machine rather than on the build agent.

So every published installer decrypted its bundle with a key printed in a public
repository. Anyone who downloaded a release could read the service account.

**Re-encrypting is not enough.** The credential itself has to be revoked, because
it may already have been taken.

### Rotating — do all four steps

1. **Issue a new key.** Firebase Console → Project Settings → Service accounts →
   *Generate new private key*.
2. **Revoke the old one.** Google Cloud Console → IAM & Admin → Service Accounts →
   the account → *Keys* → delete the previous key. Until this is done, the
   exposed credential still works, and steps 1 and 3 change nothing.
3. **Re-encrypt** the new JSON (below) and update the `FIREBASE_CREDENTIALS_ENC`
   repository secret.
4. **Distribute the passphrase** to each machine that synchronises, as the
   environment variable `ANIMALESDEASIS_CRED_KEY`. Without this the application
   runs local-only — visibly, and by design.

Also worth doing while you are there: review the Firestore security rules. They
do not restrain the Admin SDK, but they are what protects the database from
everything else.

### Re-encrypting the bundle

```bash
# A long random passphrase. There is no default: the tool refuses without one.
export ANIMALESDEASIS_CRED_KEY='...'          # bash
$env:ANIMALESDEASIS_CRED_KEY = '...'          # PowerShell

mvn -q compile
java -cp target/classes \
  com.asosiaciondeasis.animalesdeasis.Config.FirebaseCredentialsEncryptor \
  path/to/new-service-account.json

rm path/to/new-service-account.json           # the copy on disk is the risk
```

Then update the repository secret:

```bash
base64 -w0 src/main/resources/FireConfig/firebase-credentials.enc   # Linux
base64 -i  src/main/resources/FireConfig/firebase-credentials.enc   # macOS
```

Exit codes are stable, so this can be scripted: `0` success, `1` encryption
failed (usually no passphrase), `2` bad arguments.

### How the bundle is protected now

| | Before | Now |
|---|---|---|
| Cipher | AES-128-CBC, no integrity check | **AES-256-GCM**, authenticated |
| Key derivation | one unsalted SHA-256 pass | **PBKDF2-HMAC-SHA256**, random salt, 210,000 iterations |
| Missing passphrase | silently used a compiled-in constant | **fails, and says so** |
| Old bundle | would be misread as current | **identified**, with instructions |

Authentication is not a detail. Under CBC a wrong key produced garbage that only
sometimes failed to unpad, so "wrong key" and "corrupted file" were
indistinguishable — the test asserting a wrong key must fail was itself flaky
0.48% of the time. GCM rejects both, every time.

Neither the plaintext JSON nor `firebase-credentials.enc` is tracked in git; the
history was checked and neither has ever been committed. The bundle is injected
at build time from a repository secret.

### The limit none of this removes

This is a desktop application, so whatever opens the bundle has to reach the
machine running it. Encryption raises the cost of extraction. It cannot make a
credential unextractable from software you hand to people.

The only way to remove client-side admin credentials is to stop shipping them:

- **Firebase client SDK plus security rules.** The application authenticates as a
  user, and the rules — which the Admin SDK ignores — become the thing that
  actually constrains access.
- **A small backend between the application and Firestore**, holding the
  credential server-side.

Either is a larger change than a rotation, and neither is a reason to delay one.

### CI secrets

| Secret | Contents |
|---|---|
| `FIREBASE_CREDENTIALS_ENC` | base64 of `firebase-credentials.enc` |

Only one. A passphrase secret would not help: it is needed where the application
*runs*, not where it is built. That mismatch is what made the silent fallback
load-bearing, and it is why removing the fallback means local-only builds until
step 4 above is done on each machine.
