package com.example.auth.domain.social;

import java.time.Instant;
import java.util.Objects;

/**
 * Domain entity representing a linked social (OAuth provider) identity.
 * Pure POJO - no framework annotations (architecture.md § Forbidden Dependencies).
 *
 * <p>Persistence is performed via {@link com.example.auth.domain.repository.SocialIdentityRepository};
 * the JPA mapping lives in {@code infrastructure.persistence}. Factory / mutator semantics
 * mirror the prior {@code SocialIdentityJpaEntity} exactly (TASK-BE-300 behavior-neutral
 * port extraction).
 */
public class SocialIdentity {

    /** Persistence identity. {@code null} for an unsaved (new) identity. */
    private final Long id;
    private final String accountId;
    /** Tenant identifier. Must not be null (fail-closed per multi-tenancy spec, TASK-BE-229). */
    private final String tenantId;
    private final String provider;
    private final String providerUserId;
    private String providerEmail;
    private final Instant connectedAt;
    private Instant lastUsedAt;

    public SocialIdentity(Long id, String accountId, String tenantId, String provider,
                          String providerUserId, String providerEmail,
                          Instant connectedAt, Instant lastUsedAt) {
        this.id = id;
        this.accountId = Objects.requireNonNull(accountId, "accountId must not be null");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId must not be null");
        this.provider = Objects.requireNonNull(provider, "provider must not be null");
        this.providerUserId = Objects.requireNonNull(providerUserId, "providerUserId must not be null");
        this.providerEmail = providerEmail;
        this.connectedAt = Objects.requireNonNull(connectedAt, "connectedAt must not be null");
        this.lastUsedAt = Objects.requireNonNull(lastUsedAt, "lastUsedAt must not be null");
    }

    /**
     * Creates a new (unsaved) social identity: {@code tenantId} defaults to
     * {@code "fan-platform"} when null; {@code connectedAt == lastUsedAt == Instant.now()};
     * {@code id} is {@code null} until persisted.
     */
    public static SocialIdentity create(String accountId, String tenantId, String provider,
                                        String providerUserId, String providerEmail) {
        Instant now = Instant.now();
        return new SocialIdentity(null, accountId,
                tenantId != null ? tenantId : "fan-platform",
                provider, providerUserId, providerEmail, now, now);
    }

    public void updateLastUsedAt() {
        this.lastUsedAt = Instant.now();
    }

    public void updateProviderEmail(String email) {
        this.providerEmail = email;
    }

    public Long getId() { return id; }
    public String getAccountId() { return accountId; }
    public String getTenantId() { return tenantId; }
    public String getProvider() { return provider; }
    public String getProviderUserId() { return providerUserId; }
    public String getProviderEmail() { return providerEmail; }
    public Instant getConnectedAt() { return connectedAt; }
    public Instant getLastUsedAt() { return lastUsedAt; }
}
