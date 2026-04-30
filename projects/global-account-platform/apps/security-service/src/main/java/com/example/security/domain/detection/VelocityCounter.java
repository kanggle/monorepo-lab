package com.example.security.domain.detection;

/**
 * Port for the per-account failed-login counter backed by Redis.
 * Implementations must be graceful under Redis outage — return 0 and swallow.
 */
public interface VelocityCounter {

    /**
     * Increments the rolling-window failure counter for {@code accountId} and returns
     * the new value. Returns 0 if the backing store is unavailable.
     *
     * @param accountId    target account (must be non-null/non-blank; caller ensures)
     * @param windowSeconds rolling window length in seconds; TTL is set to {@code windowSeconds + 60}
     */
    long incrementAndGet(String accountId, int windowSeconds);

    /**
     * Peeks the current counter without modifying it. Returns 0 if missing or unavailable.
     */
    long peek(String accountId, int windowSeconds);
}
