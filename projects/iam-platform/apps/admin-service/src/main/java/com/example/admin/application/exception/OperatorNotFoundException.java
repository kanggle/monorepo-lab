package com.example.admin.application.exception;

/**
 * Raised when an operator row expected to exist (e.g. during 2FA enrollment
 * for an already-authenticated bootstrap context) is not present. Distinct
 * from {@link AuditFailureException} because the cause is an integrity / data
 * mismatch rather than an audit-persistence failure.
 */
public class OperatorNotFoundException extends RuntimeException {
    public OperatorNotFoundException(String message) {
        super(message);
    }
}
