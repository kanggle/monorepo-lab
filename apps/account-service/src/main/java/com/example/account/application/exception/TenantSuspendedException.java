package com.example.account.application.exception;

/**
 * TASK-BE-231: Thrown when the requested {tenantId} exists in the tenants table
 * but its status is SUSPENDED. New account creation is rejected.
 * Maps to 409 TENANT_SUSPENDED.
 */
public class TenantSuspendedException extends RuntimeException {

    private final String tenantId;

    public TenantSuspendedException(String tenantId) {
        super("Tenant is suspended and cannot accept new accounts: " + tenantId);
        this.tenantId = tenantId;
    }

    public String getTenantId() {
        return tenantId;
    }
}
