package com.example.auth.application.exception;

/**
 * Thrown internally by {@code RequestPasswordResetUseCase} when the per-email
 * rate limit (TASK-BE-144) is exhausted. The use case catches this exception
 * and absorbs it into the standard 204 response so account-existence does not
 * leak through the response shape; the throw + catch shape exists so that
 * future AOP-style metrics hooks (e.g. micrometer counter increment) can
 * subscribe to the exception event without touching the use case body.
 */
public class PasswordResetRateLimitedException extends RuntimeException {

    private final String emailHash;

    public PasswordResetRateLimitedException(String emailHash) {
        super("Password reset rate limit exceeded for emailHash=" + emailHash);
        this.emailHash = emailHash;
    }

    public String getEmailHash() {
        return emailHash;
    }
}
