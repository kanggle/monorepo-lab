package com.example.auth.domain.repository;

import com.example.auth.domain.social.SocialIdentity;

import java.util.Optional;

/**
 * Port interface for social (OAuth provider) identity persistence.
 *
 * <p>Exposes only the operations the application layer uses. The JPA-level
 * {@code findByAccountId} is intentionally not hoisted (no application caller —
 * mirrors {@link RefreshTokenRepository} exposing only used methods).
 */
public interface SocialIdentityRepository {

    Optional<SocialIdentity> findByProviderAndProviderUserId(String provider, String providerUserId);

    SocialIdentity save(SocialIdentity socialIdentity);
}
