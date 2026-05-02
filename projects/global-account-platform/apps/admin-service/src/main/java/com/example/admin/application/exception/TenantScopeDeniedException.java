package com.example.admin.application.exception;

/**
 * Thrown when an operator attempts to act on a tenant outside their scope.
 *
 * <p>TASK-BE-249: raised by the audit-query and operator-creation paths when a
 * non-platform-scope operator's {@code tenantId} does not match the requested
 * {@code targetTenantId}. Maps to HTTP 403 with error code
 * {@code TENANT_SCOPE_DENIED} in {@code AdminExceptionHandler}.
 */
public class TenantScopeDeniedException extends RuntimeException {

    public TenantScopeDeniedException(String message) {
        super(message);
    }
}
