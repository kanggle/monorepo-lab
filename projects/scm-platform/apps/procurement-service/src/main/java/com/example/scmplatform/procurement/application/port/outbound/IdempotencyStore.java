package com.example.scmplatform.procurement.application.port.outbound;

import java.util.Optional;

/**
 * Outbound port for idempotency caching (rules/traits/transactional.md T1).
 *
 * <p>Implementation contract:
 * <ul>
 *   <li>Same {@code (clientId, endpoint, idempotencyKey)} with the same
 *       payload hash → return the previously-stored response.</li>
 *   <li>Same key with a different payload hash → throw
 *       {@link com.example.scmplatform.procurement.domain.error.IdempotencyKeyMismatchException}
 *       (Edge Case #8).</li>
 *   <li>TTL: minimum 24 hours (T1).</li>
 *   <li>Failure mode: fail-CLOSED — if the store is unavailable, the calling
 *       endpoint MUST surface 503 (Failure Scenario D — idempotency guarantees
 *       outweigh availability for mutating writes).</li>
 * </ul>
 */
public interface IdempotencyStore {

    Optional<StoredResponse> findExisting(String tenantId, String endpoint, String key, String payloadHash);

    void store(String tenantId, String endpoint, String key, String payloadHash, StoredResponse response);

    record StoredResponse(int status, String body) {
    }
}
