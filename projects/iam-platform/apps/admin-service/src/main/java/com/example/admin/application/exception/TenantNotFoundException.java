package com.example.admin.application.exception;

/**
 * TASK-BE-250: Thrown when a requested tenant does not exist.
 * Maps to 404 TENANT_NOT_FOUND.
 */
public class TenantNotFoundException extends RuntimeException {

    private final String tenantId;

    public TenantNotFoundException(String tenantId) {
        super("Tenant not found: " + tenantId);
        this.tenantId = tenantId;
    }

    public TenantNotFoundException(String tenantId, Throwable cause) {
        super("Tenant not found: " + tenantId, cause);
        this.tenantId = tenantId;
    }

    public String getTenantId() {
        return tenantId;
    }
}
