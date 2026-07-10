package com.example.account.infrastructure.persistence;

import com.example.account.domain.tenant.Tenant;
import com.example.account.domain.tenant.TenantId;
import com.example.account.domain.tenant.TenantStatus;
import com.example.account.domain.tenant.TenantType;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "tenants")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TenantJpaEntity {

    @Id
    @Column(name = "tenant_id", length = 32)
    private String tenantId;

    @Column(name = "display_name", length = 100, nullable = false)
    private String displayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "tenant_type", length = 20, nullable = false)
    private TenantType tenantType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 10, nullable = false)
    private TenantStatus status;

    /**
     * TASK-BE-491 (ADR-MONO-047 § D1/D7): the grouping link to {@code org_node}.
     * NULLABLE by design — {@code null} = "ungrouped singleton" = an UNBOUNDED effective
     * ceiling = byte-identical pre-ADR-047 behaviour.
     *
     * <p>Held as a plain id rather than a {@code @ManyToOne} association: the tenant
     * aggregate must not load the org tree; the ceiling is resolved through
     * {@code OrgNodeQueryUseCase}, never by navigating from here.
     */
    @Column(name = "org_node_id", length = 36)
    private String orgNodeId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /**
     * NOTE (TASK-BE-491): this builds a DETACHED entity which {@code TenantRepositoryImpl.save}
     * hands to {@code JpaRepository.save} → {@code merge}. Every persisted column must
     * therefore be carried across, or the merge silently NULLs it. {@code orgNodeId} is
     * carried for exactly that reason: without it, an ordinary displayName/status PATCH
     * ({@code TenantProvisionUseCase.update}) would un-group the tenant.
     */
    public static TenantJpaEntity fromDomain(Tenant tenant) {
        TenantJpaEntity entity = new TenantJpaEntity();
        entity.tenantId = tenant.getTenantId().value();
        entity.displayName = tenant.getDisplayName();
        entity.tenantType = tenant.getTenantType();
        entity.status = tenant.getStatus();
        entity.orgNodeId = tenant.getOrgNodeId() == null ? null : tenant.getOrgNodeId().value();
        entity.createdAt = tenant.getCreatedAt();
        entity.updatedAt = tenant.getUpdatedAt();
        return entity;
    }

    /** Mutate displayName (TASK-BE-250 admin PATCH support). */
    public void updateDisplayName(String newDisplayName, java.time.Instant now) {
        this.displayName = newDisplayName;
        this.updatedAt = now;
    }

    /** Mutate status (TASK-BE-250 admin PATCH support). */
    public void updateStatus(TenantStatus newStatus, java.time.Instant now) {
        this.status = newStatus;
        this.updatedAt = now;
    }

    /** TASK-BE-491: attach/detach this tenant's grouping node ({@code null} = ungrouped). */
    public void assignOrgNode(String newOrgNodeId, java.time.Instant now) {
        this.orgNodeId = newOrgNodeId;
        this.updatedAt = now;
    }

    public Tenant toDomain() {
        return Tenant.reconstitute(
                new TenantId(tenantId),
                displayName,
                tenantType,
                status,
                orgNodeId == null ? null : new com.example.account.domain.orgnode.OrgNodeId(orgNodeId),
                createdAt,
                updatedAt
        );
    }
}
