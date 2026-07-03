package com.example.auth.application.exception;

/**
 * TASK-BE-470-fix-001: account-service rejected the signup as invalid — a 400/422
 * VALIDATION_ERROR (email format, password complexity per PasswordPolicy). The
 * signup page re-renders with a "check your input / password rule" message.
 */
public class SignupInvalidException extends RuntimeException {

    public SignupInvalidException(String message) {
        super(message);
    }
}
