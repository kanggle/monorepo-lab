package com.example.admin.application.exception;

/**
 * TASK-BE-250: Thrown when a requested tenantId is in the reserved word list.
 * Maps to 400 TENANT_ID_RESERVED.
 */
public class TenantIdReservedException extends RuntimeException {

    private final String tenantId;

    public TenantIdReservedException(String tenantId) {
        super("tenantId is reserved and cannot be used: " + tenantId);
        this.tenantId = tenantId;
    }

    public String getTenantId() {
        return tenantId;
    }
}
