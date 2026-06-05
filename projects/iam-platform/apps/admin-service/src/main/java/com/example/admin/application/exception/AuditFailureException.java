package com.example.admin.application.exception;

/**
 * Thrown when admin_actions INSERT fails. Triggers fail-closed behaviour:
 * the command is aborted and a 500 response is returned.
 */
public class AuditFailureException extends RuntimeException {
    public AuditFailureException(String message, Throwable cause) {
        super(message, cause);
    }

    public AuditFailureException(String message) {
        super(message);
    }
}
