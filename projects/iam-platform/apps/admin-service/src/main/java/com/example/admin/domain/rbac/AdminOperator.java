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

    /**
     * TASK-MONO-750 (ADR-MONO-079 ACCEPTED — A, D4-A, rider R3) — the fan tenant. It subscribes to
     * the {@code fan} domain (account-service V0031), so a token minted by assuming it is derived
     * {@code FAN_OPERATOR}, which opens fan artist-service's directory-management paths. R3 gives
     * that path to <b>platform</b> operators only ({@link #isPlatformScope()}), never to a
     * customer tenant's operator — so this tenant is reachable only through the platform-scope
     * sentinel, never through an assignment row or a partnership. admin-service's single home for
     * the literal.
     */
    public static final String FAN_PLATFORM_TENANT_ID = "fan-platform";

    /**
     * TASK-MONO-750: the domain keys that derive {@code FAN_OPERATOR} at assume-tenant (auth-service
     * {@code OperatorRoleDerivation}: {@code case "fan", "fan-platform"}). Only
     * {@link #FAN_PLATFORM_TENANT_ID} may subscribe to them.
     */
    public static final java.util.Set<String> FAN_DOMAIN_KEYS = java.util.Set.of("fan", "fan-platform");

    /**
     * TASK-MONO-750: {@code true} for a tenant that only a platform-scope operator may assume — no
     * assignment row and no partnership reaches it.
     */
    public static boolean isPlatformOperatorOnlyTenant(String tenantId) {
        return FAN_PLATFORM_TENANT_ID.equals(tenantId);
    }

    /**
     * TASK-MONO-750: {@code true} when subscribing {@code tenantId} to {@code domainKey} would make
     * a customer tenant's assume-tenant token carry {@code FAN_OPERATOR} — i.e. a fan domain key on
     * any tenant other than {@link #FAN_PLATFORM_TENANT_ID}.
     */
    public static boolean isReservedFanSubscription(String tenantId, String domainKey) {
        return domainKey != null
                && FAN_DOMAIN_KEYS.contains(domainKey.trim())
                && !FAN_PLATFORM_TENANT_ID.equals(tenantId);
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
