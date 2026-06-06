package com.example.security.domain.detection;

/**
 * Port for the per-tenant per-account failed-login counter backed by Redis.
 * Implementations must be graceful under Redis outage — return 0 and swallow.
 *
 * <p>TASK-BE-248 Phase 1: all operations require {@code tenantId} so that counters
 * are isolated per tenant. The Redis key format is
 * {@code security:velocity:{tenantId}:{accountId}:{windowSeconds}}.
 * Legacy keys ({@code security:velocity:{accountId}:{windowSeconds}}) are not
 * migrated — they expire naturally at TTL (windowSeconds + 60 s). During the
 * transition window detection may produce false-negatives for accounts that had
 * accumulated counts under the legacy key scheme.
 */
public interface VelocityCounter {

    /**
     * Increments the rolling-window failure counter for the given tenant/account
     * pair and returns the new value. Returns 0 if the backing store is unavailable.
     *
     * @param tenantId      tenant identifier (must be non-null/non-blank)
     * @param accountId     target account (must be non-null/non-blank; caller ensures)
     * @param windowSeconds rolling window length in seconds; TTL is set to {@code windowSeconds + 60}
     */
    long incrementAndGet(String tenantId, String accountId, int windowSeconds);

    /**
     * Peeks the current counter without modifying it. Returns 0 if missing or unavailable.
     */
    long peek(String tenantId, String accountId, int windowSeconds);
}
