package com.example.scmplatform.procurement.application.port.outbound;

import java.time.Duration;
import java.util.Optional;

/**
 * Application port for the S2 REST idempotency dedupe store (TASK-BE-445,
 * {@code rules/traits/transactional.md} T1). Backed by the {@code idempotency_keys}
 * table via {@code JpaIdempotencyStore}. Keeps the persistence type out of the
 * application layer (hexagonal boundary).
 *
 * <p>The {@code save} is expected to surface a duplicate-key insert eagerly (so a
 * concurrent first-request race rolls back rather than double-executing) — its
 * adapter flushes.
 */
public interface IdempotencyStore {

    /** Cached first-response for an idempotency key. */
    record IdempotencyRecord(String payloadHash, int responseStatus, String responseBody) {
    }

    /** Look up a stored response for {@code (tenantId, endpoint, key)}. */
    Optional<IdempotencyRecord> find(String tenantId, String endpoint, String key);

    /**
     * Persist the first response for {@code (tenantId, endpoint, key)}. Throws
     * {@link org.springframework.dao.DataIntegrityViolationException} on a
     * concurrent duplicate insert (the PK race) — the caller lets it propagate so
     * the losing transaction rolls back its side effects.
     */
    void save(String tenantId, String endpoint, String key,
              String payloadHash, int responseStatus, String responseBody, Duration ttl);
}
