package com.example.fanplatform.notification.domain.time;

import java.time.Instant;

/**
 * Time source port. The bound implementation truncates {@code now()} to
 * microseconds (§15) so an in-memory value equals a Postgres TIMESTAMPTZ
 * re-read. Tests can swap a fixed clock.
 */
@FunctionalInterface
public interface ClockPort {
    Instant now();
}
