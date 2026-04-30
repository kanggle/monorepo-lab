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
}
