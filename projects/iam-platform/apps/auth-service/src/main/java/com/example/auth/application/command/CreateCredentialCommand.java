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
 * <p>TASK-BE-330 (ADR-MONO-021 D2): {@code accountType} ({@code CONSUMER}|{@code OPERATOR})
 * is now carried from the provisioning path. When null, the use-case defaults to CONSUMER
 * (the step-1 migration default).</p>
 */
public record CreateCredentialCommand(
        String accountId,
        String email,
        String password,
        /** Tenant identifier. When null, defaults to "fan-platform". */
        String tenantId,
        /** Account classification (CONSUMER|OPERATOR). When null, defaults to CONSUMER. */
        String accountType
) {
    /**
     * @deprecated Use {@link #CreateCredentialCommand(String, String, String, String, String)}
     *             which carries accountType. Retained for backwards compatibility; defaults
     *             accountType to CONSUMER.
     */
    @Deprecated
    public CreateCredentialCommand(String accountId, String email, String password, String tenantId) {
        this(accountId, email, password, tenantId, "CONSUMER");
    }

    /**
     * @deprecated Use {@link #CreateCredentialCommand(String, String, String, String, String)}.
     *             Retained for backwards compatibility; defaults tenantId to "fan-platform"
     *             and accountType to CONSUMER.
     */
    @Deprecated
    public CreateCredentialCommand(String accountId, String email, String password) {
        this(accountId, email, password, "fan-platform", "CONSUMER");
    }
}
