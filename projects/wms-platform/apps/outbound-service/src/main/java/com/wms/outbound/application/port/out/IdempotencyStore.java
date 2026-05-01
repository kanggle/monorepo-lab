package com.wms.outbound.application.port.out;

import java.time.Duration;
import java.util.Optional;

/**
 * Out-port for the REST idempotency store (trait T1).
 *
 * <p>Authoritative reference:
 * {@code specs/services/outbound-service/idempotency.md} §1.
 *
 * <h2>Contract for {@code storageKey}</h2>
 *
 * <p>Callers pass the <em>suffix</em>
 * {@code {method}:{path_hash}:{idempotency_key}}. The adapter is responsible
 * for prepending the {@code outbound:idempotency:} prefix that defines the
 * canonical Redis key shape.
 */
public interface IdempotencyStore {

    Optional<StoredResponse> lookup(String storageKey);

    void put(String storageKey, StoredResponse response, Duration ttl);

    boolean tryAcquireLock(String storageKey, Duration ttl);

    void releaseLock(String storageKey);
}
