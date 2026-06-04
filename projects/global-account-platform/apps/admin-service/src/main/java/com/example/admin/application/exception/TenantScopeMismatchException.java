package com.example.admin.application.exception;

/**
 * TASK-BE-339 (ADR-MONO-020 D3 amendment follow-up) — thrown when the path
 * {@code tenantId} of an org_scope management request does not match the active
 * tenant ({@code X-Tenant-Id}). An operator-admin may only manage assignments
 * within their own active tenant; a mismatch is a tenant-scope boundary
 * violation distinct from {@link TenantScopeDeniedException} (which covers the
 * effective-scope membership checks on the listing/audit paths).
 *
 * <p>Maps to HTTP 403 with error code {@code TENANT_SCOPE_MISMATCH} in
 * {@code AdminExceptionHandler}.
 */
public class TenantScopeMismatchException extends RuntimeException {

    public TenantScopeMismatchException(String message) {
        super(message);
    }
}
