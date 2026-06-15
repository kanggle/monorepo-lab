package com.example.account.application.port;

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
