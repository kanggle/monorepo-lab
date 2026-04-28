package com.wms.inbound.application.port.out;

import java.time.Duration;
import java.util.Optional;

/**
 * Out-port for the REST idempotency store (trait T1).
 *
 * <p>Authoritative reference:
 * {@code specs/services/inbound-service/idempotency.md} §1.
 *
 * <h2>Contract for {@code storageKey}</h2>
 *
 * <p>Callers pass the <em>suffix</em>
 * {@code {method}:{path_hash}:{idempotency_key}} (see {@code idempotency.md}
 * §1.3). The adapter is responsible for prepending the
 * {@code inbound:idempotency:} prefix that defines the canonical Redis key
 * shape. Implementations must not double-prefix and must not strip the prefix
 * from caller input.
 *
 * <p>The full Redis prefix is {@code inbound:idempotency:} (matches
 * inventory-service's TASK-BE-025 lesson — the shorter {@code inbound:idem:}
 * shape was rejected as latent risk).
 */
public interface IdempotencyStore {

    Optional<StoredResponse> lookup(String storageKey);

    void put(String storageKey, StoredResponse response, Duration ttl);

    boolean tryAcquireLock(String storageKey, Duration ttl);

    void releaseLock(String storageKey);
}
