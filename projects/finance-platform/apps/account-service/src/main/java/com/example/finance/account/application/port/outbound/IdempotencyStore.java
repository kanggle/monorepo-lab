package com.example.finance.account.application.port.outbound;

/**
 * Idempotency dedupe port (fintech F1, transactional T1). Key scope =
 * {@code (idempotencyKey, endpoint, tenantId)} (architecture.md § Idempotency).
 *
 * <p><b>Atomic claim-before-execute</b> (architecture.md "SET NX-EX"): the
 * caller must {@link #claim} <em>before</em> performing the fund-moving action.
 * Exactly one concurrent/sequential request for a given key wins the claim and
 * executes; all others replay the winner's stored response (no fund
 * re-movement) or, for a different payload, get
 * {@code IDEMPOTENCY_KEY_CONFLICT} (409). The {@code idempotency_keys} table PK
 * is the authoritative atomic gate (a duplicate insert fails); the winner fills
 * the response via {@link #complete}, a failed action {@link #release}s the
 * claim so a retry can re-attempt.
 *
 * <ul>
 *   <li>Same key + identical payload, completed → REPLAY the stored response.</li>
 *   <li>Same key + identical payload, winner still running → IN_PROGRESS.</li>
 *   <li>Same key + different payload → CONFLICT (caller raises 409).</li>
 *   <li>Store unreachable on a mutating fund write → fail-CLOSED →
 *       {@code IDEMPOTENCY_STORE_UNAVAILABLE} (503). Idempotency guarantees
 *       outweigh availability for fund movement (F1).</li>
 *   <li>TTL ≥ 24h; an expired in-progress claim (crashed executor) is
 *       reclaimable.</li>
 * </ul>
 */
public interface IdempotencyStore {

    /**
     * Atomically claim {@code (key, endpoint, tenantId)} for this payload, or
     * report the prior state.
     *
     * @return {@link Claim.Outcome#EXECUTE} — caller WON; it must run the
     *         action then call {@link #complete} (2xx) or {@link #release};
     *         {@link Claim.Outcome#REPLAY} — a completed response exists for
     *         the SAME payload (return it, no re-movement);
     *         {@link Claim.Outcome#CONFLICT} — a row exists with a DIFFERENT
     *         payload hash (caller raises 409 IDEMPOTENCY_KEY_CONFLICT);
     *         {@link Claim.Outcome#IN_PROGRESS} — another request holds the
     *         claim for the SAME payload and has not completed yet.
     */
    Claim claim(String tenantId, String endpoint, String key, String payloadHash);

    /** Fill the won claim with the final 2xx response (UPDATE + cache). */
    void complete(String tenantId, String endpoint, String key,
                  String payloadHash, StoredResponse response);

    /** Release a won claim whose action failed, so a retry can re-claim. */
    void release(String tenantId, String endpoint, String key);

    record StoredResponse(int status, String body) {
    }

    /** Claim outcome. */
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
