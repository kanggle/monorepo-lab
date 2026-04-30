package com.example.auth.infrastructure.persistence;

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

    /**
     * Creates a new social identity with the given tenant context (TASK-BE-229).
     */
    public static SocialIdentityJpaEntity create(String accountId, String tenantId, String provider,
                                                  String providerUserId, String providerEmail) {
        SocialIdentityJpaEntity entity = new SocialIdentityJpaEntity();
        entity.accountId = accountId;
        entity.tenantId = tenantId != null ? tenantId : "fan-platform";
        entity.provider = provider;
        entity.providerUserId = providerUserId;
        entity.providerEmail = providerEmail;
        Instant now = Instant.now();
        entity.connectedAt = now;
        entity.lastUsedAt = now;
        return entity;
    }

    /**
     * @deprecated Use {@link #create(String, String, String, String, String)} which requires tenantId.
     *             Retained for backwards compatibility; defaults to "fan-platform".
     */
    @Deprecated
    public static SocialIdentityJpaEntity create(String accountId, String provider,
                                                  String providerUserId, String providerEmail) {
        return create(accountId, "fan-platform", provider, providerUserId, providerEmail);
    }

    public void updateLastUsedAt() {
        this.lastUsedAt = Instant.now();
    }

    public void updateProviderEmail(String email) {
        this.providerEmail = email;
    }
}
