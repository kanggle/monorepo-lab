package com.example.auth.domain.credentials;

/**
 * Thrown when a password fails to satisfy {@code PasswordPolicy} rules
 * (length, complexity, email-equality).
 *
 * <p>The carried message describes the specific violation for logging/error
 * mapping; it must NOT include the offending password value (R4 — no plaintext
 * password in logs).</p>
 */
public class PasswordPolicyViolationException extends RuntimeException {

    public PasswordPolicyViolationException(String message) {
        super(message);
    }
}
