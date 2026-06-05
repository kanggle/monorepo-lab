package com.example.auth.infrastructure.persistence;

import com.example.auth.domain.social.SocialIdentity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "social_identities")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialIdentityJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "account_id", nullable = false, length = 36)
    private String accountId;

    // TASK-BE-229: tenant_id added — composite unique (tenant_id, provider, provider_user_id).
    @Column(name = "tenant_id", nullable = false, length = 32)
    private String tenantId;

    @Column(name = "provider", nullable = false, length = 20)
    private String provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    @Column(name = "provider_email", length = 255)
    private String providerEmail;

    @Column(name = "connected_at", nullable = false)
    private Instant connectedAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    public SocialIdentity toDomain() {
        return new SocialIdentity(id, accountId,
                tenantId != null ? tenantId : "fan-platform",
                provider, providerUserId, providerEmail, connectedAt, lastUsedAt);
    }

    public static SocialIdentityJpaEntity fromDomain(SocialIdentity identity) {
        SocialIdentityJpaEntity entity = new SocialIdentityJpaEntity();
        entity.id = identity.getId();
        entity.accountId = identity.getAccountId();
        entity.tenantId = identity.getTenantId();
        entity.provider = identity.getProvider();
        entity.providerUserId = identity.getProviderUserId();
        entity.providerEmail = identity.getProviderEmail();
        entity.connectedAt = identity.getConnectedAt();
        entity.lastUsedAt = identity.getLastUsedAt();
        return entity;
    }
}
