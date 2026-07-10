package com.example.account.domain.tenant;

import com.example.account.domain.orgnode.OrgNodeId;
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
    /**
     * TASK-BE-491 (ADR-MONO-047 § D1/D7): the grouping node that owns this service-tenant.
     * {@code null} = ungrouped singleton ⟹ an UNBOUNDED effective ceiling ⟹ byte-identical
     * pre-ADR-047 behaviour. The tenant remains the one and only isolation key (M1) — the
     * node groups tenants, it does not nest them.
     */
    private OrgNodeId orgNodeId;
    private Instant createdAt;
    private Instant updatedAt;

    private Tenant() {
    }

    /**
     * Factory used by infrastructure mappers when reconstituting from persistence.
     *
     * <p>Overload retained so pre-ADR-047 call sites (which know nothing of the org tree)
     * keep compiling unchanged; it reconstitutes an <b>ungrouped</b> tenant.
     */
    public static Tenant reconstitute(TenantId tenantId, String displayName,
                                       TenantType tenantType, TenantStatus status,
                                       Instant createdAt, Instant updatedAt) {
        return reconstitute(tenantId, displayName, tenantType, status, null, createdAt, updatedAt);
    }

    /** TASK-BE-491: full reconstitution including the optional grouping node. */
    public static Tenant reconstitute(TenantId tenantId, String displayName,
                                       TenantType tenantType, TenantStatus status,
                                       OrgNodeId orgNodeId,
                                       Instant createdAt, Instant updatedAt) {
        Tenant tenant = new Tenant();
        tenant.tenantId = tenantId;
        tenant.displayName = displayName;
        tenant.tenantType = tenantType;
        tenant.status = status;
        tenant.orgNodeId = orgNodeId;
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

    // -----------------------------------------------------------------------
    // TASK-BE-250: mutation support (admin-service PATCH via internal API)
    // -----------------------------------------------------------------------

    /**
     * Factory for creating a brand-new tenant (initial status always ACTIVE).
     */
    public static Tenant create(TenantId tenantId, String displayName,
                                TenantType tenantType, java.time.Instant now) {
        Tenant tenant = new Tenant();
        tenant.tenantId = tenantId;
        tenant.displayName = displayName;
        tenant.tenantType = tenantType;
        tenant.status = TenantStatus.ACTIVE;
        tenant.createdAt = now;
        tenant.updatedAt = now;
        return tenant;
    }

    /**
     * Updates displayName. Caller is responsible for calling {@link Tenant#reconstitute}
     * first (i.e. always load from DB before mutating).
     */
    public void updateDisplayName(String newDisplayName, java.time.Instant now) {
        if (newDisplayName == null || newDisplayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        this.displayName = newDisplayName.trim();
        this.updatedAt = now;
    }

    /**
     * Updates status. Validates the ACTIVE ↔ SUSPENDED transition matrix.
     * Same-status update is a no-op (returns {@code false} to indicate no change).
     *
     * @return {@code true} if the status actually changed, {@code false} if it was a no-op.
     */
    public boolean updateStatus(TenantStatus newStatus, java.time.Instant now) {
        if (this.status == newStatus) {
            return false; // no-op
        }
        this.status = newStatus;
        this.updatedAt = now;
        return true;
    }

    /**
     * TASK-BE-491 (ADR-MONO-047 § D1/D7): attach this tenant to a grouping node, or detach
     * it ({@code null} = ungrouped singleton).
     *
     * <p>Grouping never changes isolation: {@code tenantId} is untouched and the token
     * still carries exactly one {@code tenant_id} (M1).
     */
    public void assignOrgNode(OrgNodeId newOrgNodeId, java.time.Instant now) {
        this.orgNodeId = newOrgNodeId;
        this.updatedAt = now;
    }
}
