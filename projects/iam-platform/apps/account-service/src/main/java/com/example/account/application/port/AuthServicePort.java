package com.example.account.application.port;

import java.util.List;

/**
 * Port for account-service → auth-service internal calls.
 *
 * <p>TASK-BE-063: auth-service owns credentials. During signup, account-service
 * calls this port so auth_db.credentials has a row to authenticate against
 * before the signup transaction commits. If the call fails the signup
 * transaction must fail so no orphan account row remains
 * ({@code specs/contracts/http/internal/auth-internal.md}).</p>
 */
public interface AuthServicePort {

    /**
     * Create a credential row for a freshly-persisted account.
     *
     * <p>TASK-MONO-263 (ADR-032 D5 step 4): the {@code accountType} parameter
     * (TASK-BE-330) is removed — the {@code account_type} claim/column is gone.</p>
     *
     * @param accountId   the account UUID (must match {@code accounts.id})
     * @param email       lower-cased email (credential lookup key)
     * @param password    plaintext password; hashed by auth-service, never stored plain
     * @param tenantId    tenant slug the credential belongs to (must match
     *                    {@code accounts.tenant_id}); when {@code null}, auth-service
     *                    defaults to {@code "fan-platform"} (public signup path).
     *                    TASK-BE-313: this parameter was missing pre-fix, causing
     *                    credentials always to fall back to {@code "fan-platform"}
     *                    regardless of the account's actual tenant scope — which
     *                    broke multi-tenant login flows (TenantProvisioningE2ETest).
     * @param identityId  TASK-BE-384 (ADR-036 M2/P3): the central {@code identity_id}
     *                    minted at account creation (M1), propagated in-band so the new
     *                    credential row is born linked to the same central identity.
     *                    {@code null} when the mint failed (fail-soft) → credential born
     *                    unlinked (reconciled later); auth-service writes it net-zero.
     * @throws CredentialAlreadyExistsConflict if auth-service reports 409 (concurrent signup)
     * @throws AuthServiceUnavailable          if auth-service is unreachable / 5xx / timeout
     */
    void createCredential(String accountId, String email, String password, String tenantId, String identityId);

    /**
     * TASK-BE-386 (ADR-MONO-036 P4, M4): bulk-propagate the central {@code identity_id}
     * to {@code auth_db.credentials} for the given {@code account_id → identity_id}
     * bindings (the cross-DB half of the production reconciliation). auth-service writes
     * each with the M2 writer — idempotent, {@code IS NULL}-guarded, no overwrite. Same-origin
     * propagation, NOT an email merge.
     *
     * @param bindings the pairs to propagate (each account's already-resolved identity)
     * @return number of credential rows that received an identity_id (already-linked /
     *         missing credentials count 0)
     * @throws AuthServiceUnavailable if auth-service is unreachable / 5xx / 4xx contract error
     */
    int backfillCredentialIdentities(List<CredentialIdentityBinding> bindings);

    /**
     * TASK-BE-618 (auth-internal.md § POST /internal/auth/consumer-pool/moves) — move the account's
     * credential from {@code siteTenantId} into the consumer pool. Called as the LAST step of one account's
     * legacy-move transaction, so that any refusal or failure (both thrown) rolls the account_db half back.
     *
     * <p>Returns normally when auth-service answered 200: moved, already in the pool (idempotent re-run),
     * or no credential to move.
     *
     * @throws CredentialPoolMoveRefused when auth-service refused with a known 409 code — the account is
     *                                   skipped with that reason
     * @throws AuthServiceUnavailable    on anything else (5xx — including admin-service being unable to
     *                                   answer the operator-facet question, 503 — timeout, unknown 4xx)
     */
    void moveCredentialToConsumerPool(String accountId, String siteTenantId);

    /**
     * TASK-BE-618 — auth-service refused the consumer-pool credential move (409). {@link #reason()} is the
     * error code without its {@code POOL_MOVE_} prefix: {@code OPERATOR_FACETED}, {@code SOCIAL_LINKED},
     * {@code POOL_CREDENTIAL_EXISTS} or {@code CREDENTIAL_TENANT_MISMATCH}.
     */
    final class CredentialPoolMoveRefused extends RuntimeException {
        private final String reason;

        public CredentialPoolMoveRefused(String accountId, String reason) {
            super("auth-service refused the consumer-pool move of accountId=" + accountId + ": " + reason);
            this.reason = reason;
        }

        public String reason() {
            return reason;
        }
    }

    /** A single {@code account_id → identity_id} propagation pair (TASK-BE-386). */
    record CredentialIdentityBinding(String accountId, String identityId) {
    }

    /** Thrown when auth-service reports 409 — concurrent signup created the credential. */
    final class CredentialAlreadyExistsConflict extends RuntimeException {
        public CredentialAlreadyExistsConflict(String accountId) {
            super("Credential already exists for accountId=" + accountId);
        }
    }

    /** Thrown when auth-service is unreachable / 5xx / timeout — signup must fail-closed. */
    final class AuthServiceUnavailable extends RuntimeException {
        public AuthServiceUnavailable(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
