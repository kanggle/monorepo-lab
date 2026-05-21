package com.example.admin.application.exception;

/**
 * TASK-BE-307 — raised when an operator attempts to mutate their own profile
 * via the admin-on-behalf-of endpoint
 * ({@code PATCH /api/admin/operators/{operatorId}/profile}) where
 * {@code {operatorId}} resolves to the caller's own {@code operator_id}.
 *
 * <p>Per spec {@code admin-api.md § PATCH /api/admin/operators/{operatorId}/profile}
 * and {@code TASK-BE-307 § Decision authority}, self-serve profile mutation
 * must go through {@code PATCH /api/admin/operators/me/profile} (BE-306) so
 * that the audit-row {@code reason} format stays distinguishable
 * ({@code <self_profile_update>} constant vs caller-typed reason).
 *
 * <p>Surfaces as {@code 400 SELF_PROFILE_UPDATE_FORBIDDEN_VIA_ADMIN_PATH} via
 * {@code AdminExceptionHandler}. Mirrors {@link SelfSuspendForbiddenException}
 * (the established self-via-admin-path rejection pattern from
 * {@code PATCH /api/admin/operators/{operatorId}/status}).
 */
public class SelfProfileUpdateForbiddenException extends RuntimeException {
    public SelfProfileUpdateForbiddenException(String message) {
        super(message);
    }
}
