package com.example.admin.infrastructure.persistence;

import com.example.admin.domain.rbac.PartnershipStatus;
import com.example.admin.domain.rbac.ScopeSet;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * TASK-BE-477 / ADR-MONO-045 D1 — JPA entity for {@code tenant_partnership}: the
 * cross-org partnership aggregate (host A ↔ partner B). Lives under
 * {@code com.example.admin.infrastructure.persistence} (the {@code JpaConfig}
 * {@code @EntityScan} / {@code @EnableJpaRepositories} base) so it registers under
 * {@code ddl-auto=validate}.
 *
 * <p>{@code delegated_scope} is a MySQL {@code JSON} column mapped via
 * {@code @JdbcTypeCode(SqlTypes.JSON)} on the {@link PartnershipScopeJson} bean
 * (mirroring {@code OperatorTenantAssignmentJpaEntity.orgScope}), converted to the
 * framework-free domain {@link ScopeSet} at the boundary.
 */
@Entity
@Table(name = "tenant_partnership")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantPartnershipJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "partnership_id", length = 36, nullable = false, unique = true)
    private String partnershipId;

    @Column(name = "host_tenant_id", length = 32, nullable = false)
    private String hostTenantId;

    @Column(name = "partner_tenant_id", length = 32, nullable = false)
    private String partnerTenantId;

    @Column(name = "status", length = 20, nullable = false)
    private String status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "delegated_scope", nullable = false)
    private PartnershipScopeJson delegatedScope;

    @Column(name = "invited_by")
    private Long invitedBy;

    @Column(name = "accepted_by")
    private Long acceptedBy;

    @Column(name = "invited_at", nullable = false)
    private Instant invitedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "terminated_at")
    private Instant terminatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(name = "version", nullable = false)
    private int version;

    /** Factory for a fresh PENDING invite. */
    public static TenantPartnershipJpaEntity createPending(String partnershipId, String hostTenantId,
                                                           String partnerTenantId, ScopeSet delegatedScope,
                                                           Long invitedBy, Instant invitedAt) {
        TenantPartnershipJpaEntity e = new TenantPartnershipJpaEntity();
        e.partnershipId = partnershipId;
        e.hostTenantId = hostTenantId;
        e.partnerTenantId = partnerTenantId;
        e.status = PartnershipStatus.PENDING.name();
        e.delegatedScope = PartnershipScopeJson.from(delegatedScope);
        e.invitedBy = invitedBy;
        e.invitedAt = invitedAt;
        e.createdAt = invitedAt;
        e.updatedAt = invitedAt;
        return e;
    }

    /** Domain view of the delegated scope. */
    public ScopeSet delegatedScopeSet() {
        return PartnershipScopeJson.toScopeSet(delegatedScope);
    }

    public PartnershipStatus statusEnum() {
        return PartnershipStatus.valueOf(status);
    }

    /**
     * Applies a lifecycle transition on the managed entity, setting the correct
     * timestamps based on the current status. The current status distinguishes
     * accept (PENDING → ACTIVE, stamps {@code accepted_by}/{@code accepted_at}) from
     * reactivate (SUSPENDED → ACTIVE, leaves acceptance fields untouched).
     */
    public void applyStatus(PartnershipStatus target, Long actorInternalId, Instant at) {
        PartnershipStatus current = statusEnum();
        switch (target) {
            case ACTIVE -> {
                if (current == PartnershipStatus.PENDING) {
                    this.acceptedBy = actorInternalId;
                    this.acceptedAt = at;
                }
                // reactivate (from SUSPENDED): keep original acceptance fields.
            }
            case TERMINATED -> this.terminatedAt = at;
            case SUSPENDED, PENDING -> {
                // no dedicated timestamp column
            }
        }
        this.status = target.name();
        this.updatedAt = at;
    }
}
