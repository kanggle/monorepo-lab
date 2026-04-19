package com.wms.master.application.port.out;

import java.time.Duration;
import java.util.Optional;

/**
 * Outbound port for idempotency storage.
 * <p>
 * Contract: see {@code specs/services/master-service/idempotency.md}.
 * <p>
 * The {@code storageKey} is the SHA-256 digest of
 * {@code "{idempotencyKey}:{method}:{path}"}, computed by the caller.
 */
public interface IdempotencyStore {

    /**
     * Returns the stored response for {@code storageKey}, if present.
     * Does not alter TTL.
     */
    Optional<StoredResponse> lookup(String storageKey);

    /**
     * Stores {@code response} under {@code storageKey} with the given TTL.
     * Overwrites any existing entry.
     */
    void put(String storageKey, StoredResponse response, Duration ttl);

    /**
     * Attempts to acquire a short-lived processing lock for {@code storageKey}.
     * <p>
     * Returns {@code true} iff the lock was acquired. Returns {@code false} if
     * another caller currently holds the lock.
     */
    boolean tryAcquireLock(String storageKey, Duration ttl);

    /**
     * Releases a previously-acquired lock. Safe to call when no lock is held.
     */
    void releaseLock(String storageKey);
}
