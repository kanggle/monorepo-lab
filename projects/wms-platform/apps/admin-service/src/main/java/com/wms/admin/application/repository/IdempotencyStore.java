package com.wms.admin.application.repository;

import java.time.Duration;
import java.util.Optional;

/**
 * Idempotency-Key cache port (Redis-backed in prod, in-memory in standalone /
 * tests). Contract: see
 * {@code specs/services/admin-service/idempotency.md § 1}.
 */
public interface IdempotencyStore {

    Optional<StoredResponse> lookup(String storageKey);

    void put(String storageKey, StoredResponse response, Duration ttl);

    boolean tryAcquireLock(String storageKey, Duration ttl);

    void releaseLock(String storageKey);
}
