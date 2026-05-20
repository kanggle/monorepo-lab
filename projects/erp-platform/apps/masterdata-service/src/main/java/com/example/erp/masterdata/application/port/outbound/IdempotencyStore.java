package com.example.erp.masterdata.application.port.outbound;

/**
 * Idempotency dedupe port (architecture.md § Idempotency). Key scope =
 * {@code (idempotencyKey, endpoint, tenantId)}.
 *
 * <p><b>Atomic claim-before-execute</b> — the caller must {@link #claim}
 * <em>before</em> performing the mutating action. Exactly one concurrent /
 * sequential request for a given key wins the claim and executes; all others
 * replay the winner's stored response (no re-mutation) or, for a different
 * payload, get {@code IDEMPOTENCY_KEY_CONFLICT} (409). The
 * {@code idempotency_keys} table PK is the authoritative atomic gate (a
 * duplicate insert fails); the winner fills the response via {@link #complete},
 * a failed action {@link #release}s the claim so a retry can re-attempt.
 *
 * <ul>
 *   <li>Same key + identical payload, completed → REPLAY the stored response.</li>
 *   <li>Same key + identical payload, winner still running → IN_PROGRESS.</li>
 *   <li>Same key + different payload → CONFLICT.</li>
 *   <li>Store unreachable on a mutating write → fail-CLOSED →
 *       {@code IDEMPOTENCY_STORE_UNAVAILABLE} (503).</li>
 *   <li>TTL ≥ 24h; an expired in-progress sentinel is reclaimable.</li>
 * </ul>
 *
 * <p>Mirrors finance/account-service's {@code IdempotencyStore} FIN-BE-004
 * final form (DB-PK-authoritative claim-before-execute).
 */
public interface IdempotencyStore {

    Claim claim(String tenantId, String endpoint, String key, String payloadHash);

    void complete(String tenantId, String endpoint, String key,
                  String payloadHash, StoredResponse response);

    void release(String tenantId, String endpoint, String key);

    record StoredResponse(int status, String body) {
    }

    record Claim(Outcome outcome, StoredResponse storedResponse) {

        public enum Outcome { EXECUTE, REPLAY, CONFLICT, IN_PROGRESS }

        public static Claim execute() {
            return new Claim(Outcome.EXECUTE, null);
        }

        public static Claim replay(StoredResponse stored) {
            return new Claim(Outcome.REPLAY, stored);
        }

        public static Claim conflict() {
            return new Claim(Outcome.CONFLICT, null);
        }

        public static Claim inProgress() {
            return new Claim(Outcome.IN_PROGRESS, null);
        }
    }
}
