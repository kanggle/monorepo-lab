package com.example.auth.application.exception;

/**
 * TASK-BE-470-fix-001: account-service returned 409 ACCOUNT_ALREADY_EXISTS to the
 * server-side signup proxy. The signup page re-renders with a "already registered"
 * message.
 */
public class SignupEmailConflictException extends RuntimeException {

    public SignupEmailConflictException(String message) {
        super(message);
    }
}
