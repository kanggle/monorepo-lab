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

    /**
     * TASK-BE-614 (multi-tenancy.md § 소비자 계정 풀, 용어): a <i>consumer site</i> is a
     * {@link TenantType#B2C_CONSUMER} tenant other than the reserved pool tenant itself
     * ({@link TenantId#CONSUMER_POOL}, which is stored as {@code B2C_CONSUMER} because the
     * {@code TenantType} enum has no third value and the pool is consumer-side storage).
     *
     * <p>Decided from the tenant row's type rather than a hard-coded {@code fan-platform} /
     * {@code ecommerce} list, so a consumer site registered later is covered without a code change.
     */
    public boolean isConsumerSite() {
        return TenantType.B2C_CONSUMER == this.tenantType
                && this.tenantId != null
                && !this.tenantId.isConsumerPool();
    }

    // -----------------------------------------------------------------------
    // TASK-BE-250: mutation support (admin-service PATCH via internal API)
    // -----------------------------------------------------------------------

    /**
     * Factory for creating a brand-new {@link TenantStatus#ACTIVE ACTIVE} tenant (the self-service
     * onboarding path and every pre-773 caller).
     */
    public static Tenant create(TenantId tenantId, String displayName,
                                TenantType tenantType, java.time.Instant now) {
        return create(tenantId, displayName, tenantType, TenantStatus.ACTIVE, now);
    }

    /**
     * TASK-MONO-773 (OD-1): factory with an explicit initial status. Only {@code ACTIVE} or
     * {@code PENDING} (SUPER_ADMIN path — «관리자 대기») may be born; {@code SUSPENDED} or null is
     * refused ({@code admin-to-account.md} § POST /internal/tenants {@code initialStatus}).
     *
     * @throws IllegalArgumentException if {@code initialStatus} is neither ACTIVE nor PENDING
     */
    public static Tenant create(TenantId tenantId, String displayName,
                                TenantType tenantType, TenantStatus initialStatus,
                                java.time.Instant now) {
        if (initialStatus != TenantStatus.ACTIVE && initialStatus != TenantStatus.PENDING) {
            throw new IllegalArgumentException(
                    "initialStatus must be ACTIVE or PENDING, was: " + initialStatus);
        }
        Tenant tenant = new Tenant();
        tenant.tenantId = tenantId;
        tenant.displayName = displayName;
        tenant.tenantType = tenantType;
        tenant.status = initialStatus;
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
     * Updates status. Same-status update is a no-op (returns {@code false} to indicate no change).
     *
     * <p>TASK-MONO-773: {@code PENDING} can never be a requested target — it only arises at
     * creation — so it is refused from every state, PENDING itself included (checked before the
     * no-op rule). {@code PENDING → ACTIVE} and {@code PENDING → SUSPENDED} are allowed; whether
     * the tenant has an admin is admin-service's judgement, not this aggregate's.
     *
     * @return {@code true} if the status actually changed, {@code false} if it was a no-op.
     * @throws IllegalArgumentException if {@code newStatus} is PENDING
     */
    public boolean updateStatus(TenantStatus newStatus, java.time.Instant now) {
        if (newStatus == TenantStatus.PENDING) {
            throw new IllegalArgumentException(
                    "status PENDING cannot be requested — it only arises at creation");
        }
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
