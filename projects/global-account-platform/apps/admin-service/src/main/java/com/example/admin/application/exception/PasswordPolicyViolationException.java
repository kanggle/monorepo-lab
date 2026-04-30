package com.example.admin.application.exception;

/**
 * Raised when a new password does not satisfy the platform password policy
 * (min 8 chars, at least 3 of 4 character categories: upper, lower, digit, special).
 * Surfaces as {@code 400 PASSWORD_POLICY_VIOLATION}.
 */
public class PasswordPolicyViolationException extends RuntimeException {
    public PasswordPolicyViolationException(String message) {
        super(message);
    }
}
