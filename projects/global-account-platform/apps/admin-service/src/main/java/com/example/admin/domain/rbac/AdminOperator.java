package com.example.admin.domain.rbac;

/**
 * Domain POJO for an admin operator. Framework-free.
 *
 * <p>TASK-BE-249: {@code tenantId} field added for multi-tenant row-level isolation.
 * SUPER_ADMIN operators carry the platform-scope sentinel {@link #PLATFORM_TENANT_ID}
 * ({@code "*"}) which allows cross-tenant operations. All other operators must only
 * access resources within their own tenant.
 */
public record AdminOperator(
        String id,
        String email,
        String displayName,
        Status status,
        long version,
        String tenantId
) {
    /**
     * Platform-scope sentinel value. An operator with this {@code tenantId} is
     * allowed to perform cross-tenant operations (i.e. SUPER_ADMIN).
     */
    public static final String PLATFORM_TENANT_ID = "*";

    public enum Status {
        ACTIVE, DISABLED, LOCKED
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    /**
     * Returns {@code true} if this operator has platform-scope (i.e. is a SUPER_ADMIN).
     * Only the exact sentinel value {@value #PLATFORM_TENANT_ID} qualifies.
     */
    public boolean isPlatformScope() {
        return PLATFORM_TENANT_ID.equals(tenantId);
    }
}
