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
        String tenantId,
        /**
         * TASK-BE-384 (ADR-036 M2/P3): optional central {@code identity_id} (born-unified).
         * When non-null the credential is born linked; null → born unlinked (net-zero).
         */
        String identityId
) {
    /**
     * @deprecated Use the canonical 5-arg constructor. Retained for backwards
     *             compatibility; defaults tenantId to "fan-platform" and identityId to null.
     */
    @Deprecated
    public CreateCredentialCommand(String accountId, String email, String password) {
        this(accountId, email, password, "fan-platform", null);
    }

    /**
     * TASK-BE-229 4-arg form (pre-BE-384). Defaults {@code identityId} to null
     * (credential born unlinked) — retained for callers that do not propagate identity.
     */
    public CreateCredentialCommand(String accountId, String email, String password, String tenantId) {
        this(accountId, email, password, tenantId, null);
    }
}
