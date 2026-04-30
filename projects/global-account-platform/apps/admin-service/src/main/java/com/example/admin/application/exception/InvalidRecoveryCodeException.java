package com.example.admin.application.exception;

/**
 * Raised when a submitted {@code recoveryCode} does not match any stored
 * Argon2id hash after optimistic-locking retry. Mapped to
 * {@code 401 INVALID_RECOVERY_CODE}.
 */
public class InvalidRecoveryCodeException extends RuntimeException {
    public InvalidRecoveryCodeException(String message) {
        super(message);
    }
}
