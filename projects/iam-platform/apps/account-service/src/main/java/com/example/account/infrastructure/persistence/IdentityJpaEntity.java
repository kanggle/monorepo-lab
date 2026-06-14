package com.example.account.infrastructure.persistence;

import com.example.account.domain.identity.Identity;
import com.example.account.domain.identity.IdentityStatus;
import com.example.account.domain.tenant.TenantId;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * JPA mapping for the central {@code identities} registry (ADR-MONO-034 U1-A,
 * V0023). Pure identity correlation — no roles/permissions (ADR-034 U5).
 */
@Entity
@Table(name = "identities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class IdentityJpaEntity {

    @Id
    @Column(name = "identity_id", length = 36)
    private String identityId;

    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(name = "primary_email", nullable = false)
    private String primaryEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IdentityStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    @Column(nullable = false)
    private int version;

    public static IdentityJpaEntity fromDomain(Identity identity) {
        IdentityJpaEntity entity = new IdentityJpaEntity();
        entity.identityId = identity.getIdentityId();
        entity.tenantId = identity.getTenantId().value();
        entity.primaryEmail = identity.getPrimaryEmail();
        entity.status = identity.getStatus();
        entity.createdAt = identity.getCreatedAt();
        entity.updatedAt = identity.getUpdatedAt();
        entity.version = identity.getVersion();
        return entity;
    }

    public Identity toDomain() {
        return Identity.reconstitute(identityId, new TenantId(tenantId), primaryEmail,
                status, createdAt, updatedAt, version);
    }
}
