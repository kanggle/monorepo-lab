package com.example.scmplatform.procurement.application;

import com.example.scmplatform.procurement.application.port.outbound.IdempotencyStore;
import com.example.scmplatform.procurement.application.port.outbound.IdempotencyStore.IdempotencyRecord;
import com.example.scmplatform.procurement.domain.error.IdempotencyKeyMismatchException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service-layer REST idempotency wrapper for procurement PO mutations
 * (TASK-BE-445, Option A). Enforces the {@code procurement-api.md} contract that
 * the JWT-authed mutating endpoints were declaring via a required
 * {@code Idempotency-Key} header but never honouring — the header value was
 * discarded, the {@code idempotency_keys} table was dead, and the 422 mismatch
 * path was unreachable, so a retried {@code POST /po} created a duplicate PO.
 *
 * <h2>Semantics</h2>
 * <ul>
 *   <li><b>first request</b> (new key) → run the action, cache the serialised
 *       response in the same transaction, return it;</li>
 *   <li><b>replay</b> (same key + same payload hash) → return the cached response;
 *       the action is NOT re-executed;</li>
 *   <li><b>mismatch</b> (same key + different payload hash) → 422
 *       {@code IDEMPOTENCY_KEY_MISMATCH};</li>
 *   <li><b>concurrent race</b> (two first-requests) → the store insert
 *       ({@code saveAndFlush}) trips the PK for the loser →
 *       {@code DataIntegrityViolationException} rolls its transaction back
 *       (including the PO it created) → 409 {@code CONFLICT}; the retry then
 *       replays the winner. Exactly one execution.</li>
 * </ul>
 *
 * <h2>Atomicity</h2>
 * <p>{@link #execute} is {@code @Transactional}; the wrapped
 * {@code PurchaseOrderApplicationService} method (also {@code @Transactional},
 * {@code REQUIRED}) JOINS this transaction, so the PO write and the
 * idempotency-record write commit together — a crash between them cannot leave a
 * PO without its dedupe record.
 *
 * <p><b>Fail-closed</b> (contract Failure Scenario D): a store error propagates
 * and rolls the transaction back rather than proceeding un-deduped — this is why
 * the fail-OPEN shared {@code libs/java-web-servlet} filter is not reused.
 *
 * <p>The Redis fast path named "primary" in the contract is a pure performance
 * optimisation over this fail-closed persistence layer and is a deliberate
 * follow-up (identical observable semantics).
 */
@Component
public class IdempotencyExecutor {

    /** Contract § Idempotency Semantics — 24h TTL. */
    private static final Duration TTL = Duration.ofHours(24);

    private final IdempotencyStore store;
    private final ObjectMapper objectMapper;

    public IdempotencyExecutor(IdempotencyStore store, ObjectMapper objectMapper) {
        this.store = store;
        this.objectMapper = objectMapper;
    }

    /**
     * Run {@code action} under idempotency protection for
     * {@code (tenantId, endpoint, key)}.
     *
     * @param successStatus the HTTP status cached for the first response (schema
     *                      requires it; the controller still owns the wire status)
     * @param type          the response view type for cache (de)serialisation
     */
    @Transactional
    public <T> T execute(String tenantId, String endpoint, String key, String payloadHash,
                         int successStatus, Class<T> type, Supplier<T> action) {
        Optional<IdempotencyRecord> found = store.find(tenantId, endpoint, key);
        if (found.isPresent()) {
            IdempotencyRecord rec = found.get();
            if (!rec.payloadHash().equals(payloadHash)) {
                throw new IdempotencyKeyMismatchException(
                        "Idempotency-Key '" + key + "' was already used with a different request payload");
            }
            return deserialize(rec.responseBody(), type);
        }
        T result = action.get();
        store.save(tenantId, endpoint, key, payloadHash, successStatus, serialize(result), TTL);
        return result;
    }

    private String serialize(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialise idempotency response", e);
        }
    }

    private <T> T deserialize(String body, Class<T> type) {
        try {
            return objectMapper.readValue(body, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to deserialise cached idempotency response", e);
        }
    }
}
