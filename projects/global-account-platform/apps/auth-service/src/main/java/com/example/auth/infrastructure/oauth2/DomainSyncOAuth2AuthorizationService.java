package com.example.auth.infrastructure.oauth2;

import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.token.RefreshToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;

import java.time.Instant;

/**
 * Decorator around any {@link OAuth2AuthorizationService} delegate that synchronises
 * SAS refresh-token issuance events into the domain {@link RefreshTokenRepository}.
 *
 * <h3>Motivation</h3>
 * <p>SAS's authorization service tracks the full OAuth 2.0 authorization
 * (including refresh tokens) but is isolated from the existing JPA-backed
 * {@link RefreshTokenRepository}. Phase 2b requires that <em>both</em> the legacy
 * {@code POST /api/auth/refresh} and the SAS {@code POST /oauth2/token
 * (grant_type=refresh_token)} flows share the same reuse-detection store.
 *
 * <h3>Strategy</h3>
 * <ul>
 *   <li>On {@link #save}: if the saved authorization contains a refresh token whose
 *       value has not yet been recorded in the domain store, persist a new
 *       {@link RefreshToken} domain entity keyed by the SAS token value (used as JTI).</li>
 *   <li>On {@link #remove}: mark the corresponding domain record as revoked.</li>
 *   <li>All other operations are delegated to the in-memory store.</li>
 * </ul>
 *
 * <h3>Rotation lifecycle</h3>
 * <p>When {@link SasRefreshTokenAuthenticationProvider} rotates a token, it:
 * <ol>
 *   <li>Calls {@code authorizationService.save(updatedAuthorization)} — which triggers
 *       this class and persists the new refresh token in the JPA store.</li>
 *   <li>Calls {@code refreshTokenRepository.findByJti(oldValue).revoke()} — to mark the
 *       old JPA record as revoked.</li>
 * </ol>
 * This ensures the JPA store always reflects the current rotation state.
 *
 * <p>TASK-BE-251 Phase 2b — SAS refresh_token grant + domain store synchronisation.
 */
@Slf4j
public class DomainSyncOAuth2AuthorizationService implements OAuth2AuthorizationService {

    private final OAuth2AuthorizationService delegate;
    private final RefreshTokenRepository refreshTokenRepository;

    public DomainSyncOAuth2AuthorizationService(
            OAuth2AuthorizationService delegate,
            RefreshTokenRepository refreshTokenRepository) {
        this.delegate = delegate;
        this.refreshTokenRepository = refreshTokenRepository;
    }

    @Override
    public void save(OAuth2Authorization authorization) {
        delegate.save(authorization);
        syncRefreshTokenToDomainStore(authorization);
    }

    @Override
    public void remove(OAuth2Authorization authorization) {
        delegate.remove(authorization);
        revokeRefreshTokenInDomainStore(authorization);
    }

    @Override
    public OAuth2Authorization findById(String id) {
        return delegate.findById(id);
    }

    @Override
    public OAuth2Authorization findByToken(String token, OAuth2TokenType tokenType) {
        return delegate.findByToken(token, tokenType);
    }

    // -----------------------------------------------------------------------
    // Sync helpers
    // -----------------------------------------------------------------------

    /**
     * Persists the refresh token into the domain JPA store if it is not already present.
     * Uses the SAS token value as the JTI (unique key).
     */
    private void syncRefreshTokenToDomainStore(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2RefreshToken> rtHolder =
                authorization.getToken(OAuth2RefreshToken.class);
        if (rtHolder == null || rtHolder.getToken() == null) {
            return;
        }

        OAuth2RefreshToken refreshToken = rtHolder.getToken();
        String tokenValue = refreshToken.getTokenValue();

        // Idempotent — skip if already persisted
        if (refreshTokenRepository.findByJti(tokenValue).isPresent()) {
            return;
        }

        String accountId = authorization.getPrincipalName();
        String tenantId = extractTenantId(authorization);

        Instant issuedAt = refreshToken.getIssuedAt() != null ? refreshToken.getIssuedAt() : Instant.now();
        Instant expiresAt = refreshToken.getExpiresAt() != null
                ? refreshToken.getExpiresAt()
                : issuedAt.plusSeconds(2592000L); // 30-day fallback

        RefreshToken domainToken = RefreshToken.create(
                tokenValue,
                accountId,
                tenantId,
                issuedAt,
                expiresAt,
                null,   // rotatedFrom — null for initially issued tokens
                null,   // deviceFingerprint — not available in SAS flow
                null    // deviceId — not available in SAS flow
        );

        try {
            refreshTokenRepository.save(domainToken);
            log.debug("SAS_SYNC: refresh token persisted to domain store. " +
                    "account={}, jti={}", accountId, tokenValue);
        } catch (Exception e) {
            // Non-fatal: log and continue. The token is still valid in SAS's in-memory store.
            // On next rotation, the reuse detector will treat absent-JPA-record as no-prior-rotation.
            log.error("SAS_SYNC: failed to persist refresh token to domain store. " +
                    "account={}, jti={}. Cause: {}", accountId, tokenValue, e.getMessage());
        }
    }

    /**
     * Marks the refresh token as revoked in the domain store when SAS removes an authorization.
     */
    private void revokeRefreshTokenInDomainStore(OAuth2Authorization authorization) {
        OAuth2Authorization.Token<OAuth2RefreshToken> rtHolder =
                authorization.getToken(OAuth2RefreshToken.class);
        if (rtHolder == null || rtHolder.getToken() == null) {
            return;
        }

        String tokenValue = rtHolder.getToken().getTokenValue();
        refreshTokenRepository.findByJti(tokenValue).ifPresent(domainToken -> {
            domainToken.revoke();
            refreshTokenRepository.save(domainToken);
            log.debug("SAS_SYNC: refresh token revoked in domain store. " +
                    "account={}, jti={}", domainToken.getAccountId(), tokenValue);
        });
    }

    /**
     * Extracts tenant_id from the authorization's stored access token claims.
     * Falls back to "fan-platform" if not available (safe default per multi-tenancy policy).
     */
    private String extractTenantId(OAuth2Authorization authorization) {
        try {
            var accessTokenHolder = authorization.getToken(
                    org.springframework.security.oauth2.core.OAuth2AccessToken.class);
            if (accessTokenHolder != null) {
                Object claims = accessTokenHolder.getClaims();
                if (claims instanceof java.util.Map<?, ?> claimsMap) {
                    Object tenantId = claimsMap.get("tenant_id");
                    if (tenantId instanceof String tid && !tid.isBlank()) {
                        return tid;
                    }
                }
            }
        } catch (Exception e) {
            log.debug("SAS_SYNC: could not extract tenant_id from authorization claims: {}",
                    e.getMessage());
        }
        return "fan-platform"; // safe default
    }
}
