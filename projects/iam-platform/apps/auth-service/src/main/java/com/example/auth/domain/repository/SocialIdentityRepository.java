package com.example.auth.domain.repository;

import com.example.auth.domain.social.SocialIdentity;
import com.example.auth.domain.tenant.TenantContext;

import java.util.Optional;

/**
 * Port interface for social (OAuth provider) identity persistence.
 *
 * <p>Exposes only the operations the application layer uses. The JPA-level
 * {@code findByAccountId} is intentionally not hoisted (no application caller —
 * mirrors {@link RefreshTokenRepository} exposing only used methods).
 */
public interface SocialIdentityRepository {

    /**
     * Looks the identity up within one tenant — the key is the unique index
     * {@code (tenant_id, provider, provider_user_id)}, so at most one row answers.
     *
     * <p>TASK-BE-611: there is deliberately no tenant-less lookup. The global
     * {@code findByProviderAndProviderUserId} it replaced let an identity created under
     * one tenant's client resolve a login started from another tenant's client — the
     * other tenant's account then entered a session stamped with this client's tenant.
     */
    Optional<SocialIdentity> findByTenantIdAndProviderAndProviderUserId(
            String tenantId, String provider, String providerUserId);

    /**
     * TASK-BE-617 (multi-tenancy.md § 소비자 계정 풀 § 1; oauth-social-login.md § 계정 연결 전략 0) — the
     * consumer-POOL identity ({@code social_identities.tenant_id = 'consumer-pool'}) a consumer-site social
     * login looks up FIRST. A named operation rather than a call site passing the literal — the same shape
     * as {@link CredentialRepository#findPoolCredentialByEmail}, so the pool lookup is findable and the
     * client-tenant lookup it precedes stays the only {@code findByTenantIdAndProviderAndProviderUserId} on
     * that path.
     */
    default Optional<SocialIdentity> findPoolIdentity(String provider, String providerUserId) {
        return findByTenantIdAndProviderAndProviderUserId(
                TenantContext.CONSUMER_POOL_TENANT_ID, provider, providerUserId);
    }

    SocialIdentity save(SocialIdentity socialIdentity);

    /**
     * TASK-BE-618 — whether ANY social identity row (in any tenant) is linked to this account. The
     * consumer-pool legacy move skips such accounts ({@code POOL_MOVE_SOCIAL_LINKED}). TASK-BE-617 made
     * the social lookup pool-aware and decided those accounts STAY site accounts — the skip is kept.
     */
    boolean existsByAccountId(String accountId);
}
