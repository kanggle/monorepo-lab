package com.example.finance.account.application.port.outbound;

import java.util.Optional;

/**
 * Idempotency dedupe port (fintech F1, transactional T1). Key scope =
 * {@code (idempotencyKey, endpoint, tenantId)} (architecture.md §
 * Idempotency).
 *
 * <p>Implementation contract:
 * <ul>
 *   <li>Same key + identical payload hash → return the stored response (NO
 *       fund re-movement).</li>
 *   <li>Same key + different payload hash → caller raises
 *       {@code IDEMPOTENCY_KEY_CONFLICT} (409).</li>
 *   <li>Redis primary (SET NX-EX); {@code idempotency_keys} table fallback
 *       when Redis is offline; both down → fail-CLOSED →
 *       {@code IDEMPOTENCY_STORE_UNAVAILABLE} (503). Idempotency guarantees
 *       outweigh availability for mutating fund writes (F1).</li>
 *   <li>TTL ≥ 24h.</li>
 * </ul>
 */
public interface IdempotencyStore {

    /**
     * Look up a prior result for {@code (key, endpoint, tenantId)}.
     *
     * @return {@link Lookup#miss()} if no row; {@link Lookup#replay} if a row
     *         with the SAME payload hash exists (replay the stored response);
     *         {@link Lookup#conflict()} if a row exists with a DIFFERENT
     *         payload hash (caller raises 409 IDEMPOTENCY_KEY_CONFLICT).
     */
    Lookup findExisting(String tenantId, String endpoint, String key, String payloadHash);

    void store(String tenantId, String endpoint, String key,
               String payloadHash, StoredResponse response);

    record StoredResponse(int status, String body) {
    }

    /** Idempotency lookup outcome. */
    record Lookup(Outcome outcome, StoredResponse storedResponse) {

        public enum Outcome { MISS, REPLAY, CONFLICT }

        public static Lookup miss() {
            return new Lookup(Outcome.MISS, null);
        }

        public static Lookup replay(StoredResponse stored) {
            return new Lookup(Outcome.REPLAY, stored);
        }

        public static Lookup conflict() {
            return new Lookup(Outcome.CONFLICT, null);
        }

        public Optional<StoredResponse> replayResponse() {
            return outcome == Outcome.REPLAY
                    ? Optional.of(storedResponse) : Optional.empty();
        }
    }
}
