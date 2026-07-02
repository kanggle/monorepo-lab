package com.example.auth.domain.tenant;

import java.util.Objects;

/**
 * Value object carrying the tenant context associated with a login or token operation.
 *
 * <p>auth-service owns its own tenant model snapshot so it does not import
 * account-service domain classes (architecture boundary rule).
 *
 * <p>Follows specs/features/multi-tenancy.md §Tenant Model.
 */
public record TenantContext(String tenantId, String tenantType) {

    /** Default tenant for the B2C fan-platform product. Used as fallback when tenant context is absent. */
    public static final String DEFAULT_TENANT_ID = "fan-platform";
    public static final String DEFAULT_TENANT_TYPE = "B2C_CONSUMER";

    /**
     * Platform-scope sentinel (ADR-002; multi-tenancy.md §Tenant Model). SUPER_ADMIN
     * cross-tenant operators carry {@code tenant_id = "*"} rather than a concrete tenant.
     * It is NOT a row in account-service's {@code tenants} table — see
     * {@link com.example.auth.infrastructure.tenant.TenantTypeResolver} for why the
     * {@code tenant_type} lookup must short-circuit on it (TASK-BE-466).
     */
    public static final String PLATFORM_SCOPE_TENANT_ID = "*";

    public TenantContext {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        Objects.requireNonNull(tenantType, "tenantType must not be null");
        if (tenantType.isBlank()) {
            throw new IllegalArgumentException("tenantType must not be blank");
        }
    }

    /**
     * Returns the default tenant context (fan-platform / B2C_CONSUMER).
     */
    public static TenantContext defaultContext() {
        return new TenantContext(DEFAULT_TENANT_ID, DEFAULT_TENANT_TYPE);
    }

    // TASK-BE-407: the static resolveTenantType(tenantId) hardcoded fallback was
    // removed — it misclassified every non-"fan-platform" tenant as B2B_ENTERPRISE.
    // tenant_type is now resolved from account-service's authoritative
    // tenants.tenant_type via
    // com.example.auth.infrastructure.tenant.TenantTypeResolver. DEFAULT_TENANT_ID /
    // DEFAULT_TENANT_TYPE remain in use for defaultContext() and as the resolver's
    // pre-seeded cache entry for the B2C fan-platform hot path.
}
