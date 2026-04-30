package com.example.admin.application.exception;

/**
 * Raised when the supplied current password does not match the stored hash
 * during {@code PATCH /api/admin/operators/me/password}.
 * Surfaces as {@code 400 CURRENT_PASSWORD_MISMATCH}.
 */
public class CurrentPasswordMismatchException extends RuntimeException {
    public CurrentPasswordMismatchException() {
        super("Current password is incorrect");
    }
}
