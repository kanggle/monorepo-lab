package com.example.security.domain.detection;

/**
 * Port for the per-tenant per-account token-reuse counter backed by Redis.
 * Implementations must be graceful under Redis outage — return 0 and swallow
 * (the rule still fires at score 100 regardless of count: token reuse is a
 * confirmed compromise, not a threshold-driven heuristic).
 *
 * <p>TASK-BE-259: counters are isolated per tenant so that a burst of reuse
 * events for one tenant never contributes to another tenant's frequency
 * tracking. The Redis key format is
 * {@code reuse:{tenantId}:{accountId}} with a 1-hour TTL — long enough to
 * surface attack patterns in dashboards/alerts, short enough that a benign
 * lull naturally clears the counter.</p>
 *
 * <p>The previous global key pattern ({@code reuse:{accountId}}) is replaced
 * outright; legacy keys (if any existed in pre-TASK-BE-259 deploys) are not
 * migrated and expire naturally at TTL.</p>
 */
public interface TokenReuseCounter {

    /**
     * Increments the per-tenant per-account reuse counter and returns the new
     * value. Returns 0 if the backing store is unavailable.
     *
     * @param tenantId  tenant identifier (must be non-null/non-blank; caller
     *                  ensures — events without {@code tenantId} are routed to
     *                  the DLQ before reaching the rule)
     * @param accountId target account (must be non-null/non-blank; caller ensures)
     */
    long incrementAndGet(String tenantId, String accountId);

    /**
     * Peeks the current counter without modifying it. Returns 0 if missing or
     * the backing store is unavailable.
     */
    long peek(String tenantId, String accountId);
}
