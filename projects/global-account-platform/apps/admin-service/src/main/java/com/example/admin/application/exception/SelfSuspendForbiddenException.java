package com.example.admin.application.exception;

/**
 * Raised when an operator attempts to transition their own account to
 * {@code SUSPENDED} via {@code PATCH /api/admin/operators/{id}/status}.
 * Surfaces as {@code 400 SELF_SUSPEND_FORBIDDEN}.
 */
public class SelfSuspendForbiddenException extends RuntimeException {
    public SelfSuspendForbiddenException(String message) {
        super(message);
    }
}
