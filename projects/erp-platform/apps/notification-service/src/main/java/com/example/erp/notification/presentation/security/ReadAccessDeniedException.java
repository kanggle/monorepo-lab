package com.example.erp.notification.presentation.security;

/**
 * Raised when a caller fails the READ authorization gate (no {@code erp.read}
 * scope, not an operator, not entitled). Maps to 403 {@code PERMISSION_DENIED}
 * (E6 fail-closed).
 */
public class ReadAccessDeniedException extends RuntimeException {

    public static final String CODE = "PERMISSION_DENIED";

    public ReadAccessDeniedException(String message) {
        super(message);
    }
}
