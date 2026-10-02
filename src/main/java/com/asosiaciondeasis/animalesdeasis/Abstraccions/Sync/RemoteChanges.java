package com.asosiaciondeasis.animalesdeasis.Abstraccions.Sync;

import java.time.Instant;
import java.util.List;

/**
 * What one read of the shared copy returned.
 *
 * @param readAt the server's time when it answered. The next read resumes from
 *               here, which is why it is the server's clock and not this
 *               machine's: stamps are compared against it, and they are the
 *               server's too
 */
public record RemoteChanges(List<RemoteAnimal> animals, Instant readAt) {
}
