package com.example.web.idempotency;

import java.time.Duration;
import java.util.Optional;

/**
 * Storage contract for the REST Idempotency-Key lifecycle: a keyed store of
 * cached responses plus a short-lived lock to serialise concurrent first-time
 * requests for the same key.
 *
 * <p>Lifted into {@code libs/java-web-servlet} so the four WMS write services
 * share one filter (ADR-MONO-038 I2). Each service keeps its own adapter
 * (Redis / in-memory) implementing this interface; the interface itself is
 * project-agnostic — a generic {@code String key → StoredResponse} store with
 * locking, no domain content.
 *
 * <p>Implementations may throw on backing-store failure; {@link IdempotencyKeyFilter}
 * treats any thrown exception as a fail-open signal (log + proceed without the
 * idempotency guarantee) per the WMS availability-over-correctness posture.
 */
public interface IdempotencyStore {

    /**
     * Returns the cached response for {@code key}, or empty if none is stored
     * (or only a lock is held).
     */
    Optional<StoredResponse> lookup(String key);

    /**
     * Attempts to acquire a processing lock for {@code key}.
     *
     * @return {@code true} if the lock was acquired; {@code false} if another
     *     request already holds it (the caller should respond 503 PROCESSING)
     */
    boolean tryAcquireLock(String key, Duration ttl);

    /**
     * Stores {@code response} against {@code key} for {@code ttl}.
     */
    void put(String key, StoredResponse response, Duration ttl);

    /**
     * Releases the processing lock for {@code key} (no-op if not held).
     */
    void releaseLock(String key);
}
