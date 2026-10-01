package com.example.admin.domain.rbac;

/**
 * Domain POJO for an admin operator. Framework-free.
 *
 * <p>TASK-BE-249: {@code tenantId} field added for multi-tenant row-level isolation.
 * SUPER_ADMIN operators carry the platform-scope sentinel {@link #PLATFORM_TENANT_ID}
 * ({@code "*"}) which allows cross-tenant operations. All other operators must only
 * access resources within their own tenant.
 *
 * <p>TASK-BE-304: {@code financeDefaultAccountId} (nullable) carries the operator's
 * chosen default finance-platform account UUID — emitted on the
 * {@code GET /api/admin/console/registry} finance product item as
 * {@code operatorContext.defaultAccountId}. {@code null} = not configured
 * (Operator Overview finance card stays {@code forbidden / MISSING_PREREQUISITE}
 * per MVP option (b) of {@code console-integration-contract.md § 2.4.9.1}).
 *
 * @param financeDefaultAccountId opaque foreign-system UUID; GAP does NOT
 *                                verify against finance-platform — stale ids
 *                                surface as finance 404 ACCOUNT_NOT_FOUND.
 */
public record AdminOperator(
        String id,
        String email,
        String displayName,
        Status status,
        long version,
        String tenantId,
        String financeDefaultAccountId
) {
    /**
     * Platform-scope sentinel value. An operator with this {@code tenantId} is
     * allowed to perform cross-tenant operations (i.e. SUPER_ADMIN).
     */
    public static final String PLATFORM_TENANT_ID = "*";

    /**
     * TASK-BE-614 (ADR-MONO-078 A; multi-tenancy.md § 소비자 계정 풀 § 1) — the reserved tenant
     * the consumer-account pool is STORED under. It is a real, ACTIVE {@code tenants} row in
     * account-service (V0029) but never a tenant anyone operates in or assumes: it must never
     * appear in a token. admin-service's single home for the literal; every surface that would
     * make it an operator/assume target refuses it ({@link #isConsumerPool(String)}).
     */
    public static final String CONSUMER_POOL_TENANT_ID = "consumer-pool";

    /** TASK-BE-614: {@code true} for {@link #CONSUMER_POOL_TENANT_ID}. */
    public static boolean isConsumerPool(String tenantId) {
        return CONSUMER_POOL_TENANT_ID.equals(tenantId);
    }

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

    /**
     * TASK-BE-306: immutable-record factory that returns a new instance with
     * {@link #financeDefaultAccountId} set to {@code newValue} (a UUID v7
     * string to set, or {@code null} to clear). All other fields are
     * byte-identical to this instance. The caller is responsible for
     * persistence — this method does NOT touch the database.
     *
     * @param newValue the new value (opaque UUID string), or {@code null} to clear
     * @return a new {@link AdminOperator} record with the field updated
     */
    public AdminOperator withFinanceDefaultAccountId(String newValue) {
        return new AdminOperator(
                id,
                email,
                displayName,
                status,
                version,
                tenantId,
                newValue);
    }
}
