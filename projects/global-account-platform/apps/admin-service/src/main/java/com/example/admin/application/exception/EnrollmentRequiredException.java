package com.example.admin.application.exception;

/**
 * Signals that login password verification succeeded but the operator has no
 * {@code admin_operator_totp} row and at least one of their roles requires
 * 2FA. Mapped to 401 {@code ENROLLMENT_REQUIRED} with a bootstrap token in
 * the response body.
 */
public class EnrollmentRequiredException extends RuntimeException {
    private final String bootstrapToken;
    private final long expiresInSeconds;

    public EnrollmentRequiredException(String bootstrapToken, long expiresInSeconds) {
        super("Operator must complete 2FA enrollment before login");
        this.bootstrapToken = bootstrapToken;
        this.expiresInSeconds = expiresInSeconds;
    }

    public String getBootstrapToken() {
        return bootstrapToken;
    }

    public long getExpiresInSeconds() {
        return expiresInSeconds;
    }
}
