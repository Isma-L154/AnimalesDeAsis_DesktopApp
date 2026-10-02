package com.asosiaciondeasis.animalesdeasis.Abstraccions.Sync;

import java.time.Instant;

/**
 * How far this installation has read the shared copy.
 *
 * @param readUpTo     the server's time at the last pull, which is where the
 *                     next one resumes; {@code null} before the first
 * @param lastFullPull when everything was last read, by this machine's clock;
 *                     {@code null} if it never was
 */
public record SyncState(Instant readUpTo, Instant lastFullPull) {

    public static SyncState none() {
        return new SyncState(null, null);
    }
}
