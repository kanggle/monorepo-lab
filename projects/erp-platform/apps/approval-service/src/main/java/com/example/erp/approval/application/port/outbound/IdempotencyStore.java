package com.example.erp.approval.application.port.outbound;

/**
 * Idempotency dedupe port (architecture.md § Idempotency). Key scope =
 * {@code (idempotencyKey, endpoint, tenantId)}.
 *
 * <p><b>Atomic claim-before-execute</b> — the caller must {@link #claim} BEFORE
 * performing the mutating transition. Exactly one concurrent / sequential
 * request for a given key wins the claim and executes; all others replay the
 * winner's stored response (no re-transition — E4 "동일 전이의 중복 요청은 최초
 * 결과를 반환하고 상태를 재전이시키지 않는다") or, for a different payload, get
 * {@code IDEMPOTENCY_KEY_CONFLICT} (409). The {@code idempotency_keys} table PK
 * is the authoritative atomic gate.
 *
 * <p>Mirrors finance/account-service + masterdata-service's
 * {@code IdempotencyStore} FIN-BE-004 final form (DB-PK-authoritative).
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
