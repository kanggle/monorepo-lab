package com.example.account.application.exception;

/**
 * TASK-BE-250: Thrown when a tenant with the requested tenantId already exists.
 * Maps to 409 TENANT_ALREADY_EXISTS.
 */
public class TenantAlreadyExistsException extends RuntimeException {

    private final String tenantId;

    public TenantAlreadyExistsException(String tenantId) {
        super("Tenant already exists: " + tenantId);
        this.tenantId = tenantId;
    }

    public String getTenantId() {
        return tenantId;
    }
}
