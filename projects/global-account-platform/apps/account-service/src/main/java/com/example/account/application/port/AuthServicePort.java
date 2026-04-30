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
     * @param accountId the account UUID (must match {@code accounts.id})
     * @param email     lower-cased email (credential lookup key)
     * @param password  plaintext password; hashed by auth-service, never stored plain
     * @throws CredentialAlreadyExistsConflict if auth-service reports 409 (concurrent signup)
     * @throws AuthServiceUnavailable          if auth-service is unreachable / 5xx / timeout
     */
    void createCredential(String accountId, String email, String password);

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
