package com.example.fanplatform.membership.domain.time;

import java.time.Instant;

/**
 * Time source port. {@link #now()} returns an {@link Instant} truncated to
 * microseconds (§15) so the in-memory subscribe response equals the DB re-read
 * (Postgres TIMESTAMPTZ stores microsecond precision; a nanosecond
 * {@code Instant.now()} would round-trip differently and fail an IT that
 * compares the response body to a DB re-read).
 */
public interface ClockPort {

    /** @return the current instant truncated to microseconds (UTC). */
    Instant now();
}
