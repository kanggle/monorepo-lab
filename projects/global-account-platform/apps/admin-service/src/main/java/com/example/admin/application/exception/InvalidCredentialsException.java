package com.example.admin.application.exception;

/**
 * Raised by the login flow when the operator lookup misses or the Argon2id
 * password check fails. Mapped to {@code 401 INVALID_CREDENTIALS} by
 * {@code AdminExceptionHandler}. Intentionally carries no detail so the
 * response shape cannot be used to distinguish "unknown operator" from
 * "bad password" (timing-attack surface is further mitigated by a dummy
 * PasswordHasher.verify on the miss path).
 */
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() {
        super("Invalid operator credentials");
    }
}
