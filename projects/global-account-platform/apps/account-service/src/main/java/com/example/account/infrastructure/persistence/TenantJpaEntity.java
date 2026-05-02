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

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public static TenantJpaEntity fromDomain(Tenant tenant) {
        TenantJpaEntity entity = new TenantJpaEntity();
        entity.tenantId = tenant.getTenantId().value();
        entity.displayName = tenant.getDisplayName();
        entity.tenantType = tenant.getTenantType();
        entity.status = tenant.getStatus();
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

    public Tenant toDomain() {
        return Tenant.reconstitute(
                new TenantId(tenantId),
                displayName,
                tenantType,
                status,
                createdAt,
                updatedAt
        );
    }
}
