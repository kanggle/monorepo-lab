package com.example.admin.application.exception;

/**
 * TASK-MONO-773 S4 (admin-api.md § Tenant Lifecycle) — a tenant-status refusal that answers {@code 409} with its own
 * code: {@link #FIRST_ADMIN_PENDING} ({@code PATCH status=ACTIVE} on a tenant whose first admin has not accepted —
 * ADR-044 D3) and {@link #NOT_PENDING} (a re-invite into a tenant that is not {@code PENDING}).
 */
public class TenantStatusConflictException extends RuntimeException {

    public static final String FIRST_ADMIN_PENDING = "TENANT_FIRST_ADMIN_PENDING";
    public static final String NOT_PENDING = "TENANT_NOT_PENDING";

    private final String code;

    private TenantStatusConflictException(String code, String message) {
        super(message);
        this.code = code;
    }

    public static TenantStatusConflictException firstAdminPending(String tenantId) {
        return new TenantStatusConflictException(FIRST_ADMIN_PENDING,
                "Tenant " + tenantId + " has no accepted first admin — it cannot become ACTIVE"
                        + " (accept the first-admin invitation, re-invite, or clean it up)");
    }

    public static TenantStatusConflictException notPending(String tenantId, String status) {
        return new TenantStatusConflictException(NOT_PENDING,
                "Tenant " + tenantId + " is " + status + ", not PENDING — invite through the ordinary invitation");
    }

    public String getCode() {
        return code;
    }
}
