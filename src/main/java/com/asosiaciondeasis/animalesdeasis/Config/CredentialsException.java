package com.asosiaciondeasis.animalesdeasis.Config;

/**
 * Raised when the Firebase bundle cannot be opened, saying which of the possible
 * reasons it was.
 *
 * <p>A distinct type because the three causes need three different responses and
 * used to be indistinguishable: everything returned {@code null} and the
 * application went quietly offline. Someone whose passphrase was never set and
 * someone whose bundle is from the old format both saw the same nothing.</p>
 */
public class CredentialsException extends Exception {

    public enum Reason {
        /** No bundle is present. Offline-only is the intended configuration. */
        MISSING_BUNDLE,
        /** A bundle exists but no passphrase was provided to open it. */
        NO_PASSPHRASE,
        /** The bundle predates the current format and has to be re-encrypted. */
        LEGACY_FORMAT,
        /** The passphrase is wrong, or the bundle has been altered. */
        UNREADABLE
    }

    private final Reason reason;

    public CredentialsException(Reason reason, String message) {
        super(message);
        this.reason = reason;
    }

    public CredentialsException(Reason reason, String message, Throwable cause) {
        super(message, cause);
        this.reason = reason;
    }

    public Reason reason() {
        return reason;
    }
}
