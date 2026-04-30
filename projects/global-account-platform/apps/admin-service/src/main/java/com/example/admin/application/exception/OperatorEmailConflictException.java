package com.example.admin.application.exception;

/**
 * Raised when {@code POST /api/admin/operators} receives an email that is
 * already bound to another {@code admin_operators} row. Surfaces as
 * {@code 409 OPERATOR_EMAIL_CONFLICT}.
 */
public class OperatorEmailConflictException extends RuntimeException {
    public OperatorEmailConflictException(String message) {
        super(message);
    }
}
