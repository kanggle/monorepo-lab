package com.example.auth.application.command;

/**
 * Command for creating a credential row in auth_db.credentials.
 *
 * <p>Issued by account-service during signup (TASK-BE-063) via the internal
 * endpoint {@code POST /internal/auth/credentials}. The password is accepted in
 * plain text on the internal boundary only; auth-service is responsible for
 * argon2id-hashing it before persistence and must never log it.</p>
 *
 * <p>TASK-BE-229: {@code tenantId} is now required. When absent (legacy callers),
 * the use-case defaults to "fan-platform".</p>
 *
 * <p>TASK-MONO-263 (ADR-032 D5 step 4): the {@code accountType} field (TASK-BE-330)
 * is removed — the {@code account_type} claim/column is gone.</p>
 */
public record CreateCredentialCommand(
        String accountId,
        String email,
        String password,
        /** Tenant identifier. When null, defaults to "fan-platform". */
        String tenantId
) {
    /**
     * @deprecated Use {@link #CreateCredentialCommand(String, String, String, String)}.
     *             Retained for backwards compatibility; defaults tenantId to "fan-platform".
     */
    @Deprecated
    public CreateCredentialCommand(String accountId, String email, String password) {
        this(accountId, email, password, "fan-platform");
    }
}
