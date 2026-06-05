package com.example.account.application.exception;

/**
 * TASK-BE-231: Thrown when the requested {tenantId} path parameter does not
 * correspond to a registered tenant in the tenants table.
 * Maps to 404 TENANT_NOT_FOUND.
 */
public class TenantNotFoundException extends RuntimeException {

    private final String tenantId;

    public TenantNotFoundException(String tenantId) {
        super("Tenant not found: " + tenantId);
        this.tenantId = tenantId;
    }

    public String getTenantId() {
        return tenantId;
    }
}
