package com.example.admin.application.exception;

/**
 * Raised when the login request violates the mutual exclusion between
 * {@code totpCode} and {@code recoveryCode} (both present or both absent when
 * 2FA is required). Mapped to 400 {@code BAD_REQUEST}.
 */
public class InvalidLoginRequestException extends RuntimeException {
    public InvalidLoginRequestException(String message) {
        super(message);
    }
}
