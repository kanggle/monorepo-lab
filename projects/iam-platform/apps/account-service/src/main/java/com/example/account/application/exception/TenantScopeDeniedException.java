package com.example.account.application.exception;

/**
 * TASK-BE-231: Thrown when the caller's tenant scope does not match the path {tenantId}.
 * Defense-in-depth: the controller re-validates even if the gateway already checked.
 * Maps to 403 TENANT_SCOPE_DENIED.
 */
public class TenantScopeDeniedException extends RuntimeException {

    public TenantScopeDeniedException(String callerTenantId, String pathTenantId) {
        super("Tenant scope denied: caller tenant '" + callerTenantId
                + "' cannot access path tenant '" + pathTenantId + "'");
    }
}
