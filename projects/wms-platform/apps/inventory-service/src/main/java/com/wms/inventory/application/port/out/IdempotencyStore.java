package com.wms.inventory.application.port.out;

import java.time.Duration;
import java.util.Optional;

/**
 * Out-port for the REST idempotency store (trait T1).
 *
 * <p>Authoritative reference:
 * {@code specs/services/inventory-service/idempotency.md} §1.
 *
 * <h2>Contract for {@code storageKey}</h2>
 *
 * <p>Callers pass the <em>suffix</em>
 * {@code {method}:{path_hash}:{idempotency_key}} (see {@code idempotency.md:49}).
 * The adapter is responsible for prepending the
 * {@code inventory:idempotency:} prefix that defines the canonical Redis key
 * shape. Implementations must not double-prefix and must not strip the prefix
 * from caller input.
 *
 * <p>Conceptual lifecycle:
 * <ol>
 *   <li>Caller resolves {@code storageKey} as
 *       {@code {method}:{path_hash}:{idempotency_key}}.</li>
 *   <li>{@link #tryAcquireLock(String, Duration)} on the lock key prevents
 *       concurrent duplicate processing.</li>
 *   <li>On success, {@link #put(String, StoredResponse, Duration)} caches the
 *       finished response for replay.</li>
 *   <li>{@link #lookup(String)} returns the cached response on subsequent
 *       calls within the TTL window.</li>
 * </ol>
 */
public interface IdempotencyStore {

    Optional<StoredResponse> lookup(String storageKey);

    void put(String storageKey, StoredResponse response, Duration ttl);

    boolean tryAcquireLock(String storageKey, Duration ttl);

    void releaseLock(String storageKey);
}
