package com.example.account.domain.tenant;

import lombok.Getter;

import java.time.Instant;

/**
 * Aggregate root representing a platform tenant.
 *
 * <p>A tenant is a product or system that shares the account platform infrastructure.
 * Examples: {@code fan-platform} (B2C), {@code wms} (B2B enterprise).
 *
 * <p>Tenants are registered exclusively by SUPER_ADMIN operators via admin-service.
 * This domain class is read-only from account-service's perspective in TASK-BE-228.
 * Tenant creation/suspension is handled in a subsequent admin-service task.
 *
 * <p>The {@code tenantId} is immutable once assigned — see TenantId Javadoc.
 */
@Getter
public class Tenant {

    private TenantId tenantId;
    private String displayName;
    private TenantType tenantType;
    private TenantStatus status;
    private Instant createdAt;
    private Instant updatedAt;

    private Tenant() {
    }

    /**
     * Factory used by infrastructure mappers when reconstituting from persistence.
     */
    public static Tenant reconstitute(TenantId tenantId, String displayName,
                                       TenantType tenantType, TenantStatus status,
                                       Instant createdAt, Instant updatedAt) {
        Tenant tenant = new Tenant();
        tenant.tenantId = tenantId;
        tenant.displayName = displayName;
        tenant.tenantType = tenantType;
        tenant.status = status;
        tenant.createdAt = createdAt;
        tenant.updatedAt = updatedAt;
        return tenant;
    }

    /**
     * Returns {@code true} when this tenant's status is {@link TenantStatus#ACTIVE}.
     * SUSPENDED tenants must not accept new logins or signups.
     */
    public boolean isActive() {
        return TenantStatus.ACTIVE == this.status;
    }
}
