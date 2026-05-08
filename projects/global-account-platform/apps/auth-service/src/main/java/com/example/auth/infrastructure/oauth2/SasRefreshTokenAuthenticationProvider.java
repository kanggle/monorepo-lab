package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.domain.repository.BulkInvalidationStore;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper;
import com.example.auth.domain.session.DeviceSession;
import com.example.auth.domain.session.RevokeReason;
import com.example.auth.domain.token.RefreshToken;
import com.example.auth.domain.token.TokenReuseDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Custom {@link AuthenticationProvider} for the {@code refresh_token} grant type.
 *
 * <p>Replaces SAS's built-in {@code OAuth2RefreshTokenAuthenticationProvider} to integrate
 * refresh-token rotation and reuse-detection with the existing domain infrastructure:
 * <ul>
 *   <li>{@link RefreshTokenRepository} — JPA-backed refresh token store shared with the
 *       legacy {@code POST /api/auth/refresh} flow</li>
 *   <li>{@link TokenReuseDetector} — pure domain service that detects replay attacks</li>
 *   <li>{@link AuthEventPublisher} — outbox-based event publishing
 *       ({@code auth.token.refreshed}, {@code auth.token.reuse.detected})</li>
 * </ul>
 *
 * <p><b>Dual-store strategy:</b> SAS's in-memory {@link OAuth2AuthorizationService} holds the
 * full authorization object (scopes, code verifier, etc.). The custom {@link RefreshTokenRepository}
 * holds the refresh token record keyed by the token's {@code tokenValue} (used as the JTI).
 * Both stores must be consistent: on rotation, the old token is blacklisted in the JPA store
 * and a new token record is written.
 *
 * <p><b>Security ordering:</b>
 * <ol>
 *   <li>Reuse detection — runs first (security-critical, same ordering as legacy RefreshTokenUseCase)</li>
 *   <li>Expired/revoked check</li>
 *   <li>Tenant mismatch check</li>
 *   <li>Rotation: generate new tokens, persist new JPA record, revoke old JPA record</li>
 *   <li>Event publishing (auth.token.refreshed or auth.token.reuse.detected)</li>
 * </ol>
 *
 * <p>TASK-BE-251 Phase 2b — SAS refresh_token grant integration.
 */
@Slf4j
public class SasRefreshTokenAuthenticationProvider implements AuthenticationProvider {

    private static final String ACTOR_TYPE_SYSTEM = "SYSTEM";

    private final OAuth2AuthorizationService authorizationService;
    private final OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;
    private final RefreshTokenRepository refreshTokenRepository;
    private final TokenReuseDetector tokenReuseDetector;
    private final BulkInvalidationStore bulkInvalidationStore;
    private final DeviceSessionRepository deviceSessionRepository;
    private final AuthEventPublisher authEventPublisher;

    public SasRefreshTokenAuthenticationProvider(
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            RefreshTokenRepository refreshTokenRepository,
            TokenReuseDetector tokenReuseDetector,
            BulkInvalidationStore bulkInvalidationStore,
            DeviceSessionRepository deviceSessionRepository,
            AuthEventPublisher authEventPublisher) {
        this.authorizationService = authorizationService;
        this.tokenGenerator = tokenGenerator;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenReuseDetector = tokenReuseDetector;
        this.bulkInvalidationStore = bulkInvalidationStore;
        this.deviceSessionRepository = deviceSessionRepository;
        this.authEventPublisher = authEventPublisher;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2RefreshTokenAuthenticationToken refreshTokenAuthentication =
                (OAuth2RefreshTokenAuthenticationToken) authentication;

        // Validate the authenticating client
        OAuth2ClientAuthenticationToken clientPrincipal =
                getAuthenticatedClientElseThrowInvalidClient(refreshTokenAuthentication);
        RegisteredClient registeredClient = clientPrincipal.getRegisteredClient();

        // Validate grant type support
        if (registeredClient == null ||
                !registeredClient.getAuthorizationGrantTypes().contains(AuthorizationGrantType.REFRESH_TOKEN)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
        }

        String submittedTokenValue = refreshTokenAuthentication.getRefreshToken();

        // Look up the authorization from SAS's store
        OAuth2Authorization authorization = authorizationService.findByToken(
                submittedTokenValue, OAuth2TokenType.REFRESH_TOKEN);
        if (authorization == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }

        // Verify the token belongs to this client
        if (!registeredClient.getId().equals(authorization.getRegisteredClientId())) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }

        OAuth2Authorization.Token<OAuth2RefreshToken> refreshTokenHolder =
                authorization.getToken(OAuth2RefreshToken.class);
        if (refreshTokenHolder == null || !refreshTokenHolder.isActive()) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }

        // --- SECURITY: Reuse detection (runs first, fail-closed) ---
        Optional<RefreshToken> existingDomainTokenOpt =
                refreshTokenRepository.findByJti(submittedTokenValue);

        if (existingDomainTokenOpt.isPresent()) {
            RefreshToken existingDomainToken = existingDomainTokenOpt.get();

            if (tokenReuseDetector.isReuse(existingDomainToken)) {
                handleReuseDetected(existingDomainToken, submittedTokenValue, authorization);
                throw new OAuth2AuthenticationException(new OAuth2Error(
                        OAuth2ErrorCodes.INVALID_GRANT,
                        "Refresh token reuse detected — all sessions have been revoked.",
                        null));
            }

            // Revoked check
            if (existingDomainToken.isRevoked()) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
            }

            // Expired check
            if (existingDomainToken.isExpired()) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
            }

            // Cross-tenant check — the tenant of the token must match the registered client's tenant
            String expectedTenant = extractClientTenantId(registeredClient);
            if (expectedTenant != null && !expectedTenant.isBlank()) {
                String tokenTenant = existingDomainToken.getTenantId();
                if (!expectedTenant.equals(tokenTenant)) {
                    log.warn("SAS_REFRESH: cross-tenant attempt detected. " +
                                    "clientTenant={}, tokenTenant={}, jti={}",
                            expectedTenant, tokenTenant, submittedTokenValue);
                    authEventPublisher.publishTokenTenantMismatch(
                            authorization.getPrincipalName(),
                            tokenTenant, expectedTenant,
                            submittedTokenValue,
                            "masked", "unknown");
                    throw new OAuth2AuthenticationException(new OAuth2Error(
                            OAuth2ErrorCodes.INVALID_GRANT,
                            "TOKEN_TENANT_MISMATCH",
                            null));
                }
            }
        }
        // If not found in domain store, we proceed — the token was just issued and not yet
        // persisted (rare race case); SAS's own expiry check already ran above.

        // --- Generate new access token ---
        Set<String> authorizedScopes = authorization.getAuthorizedScopes();
        Authentication principal = authorization.getAttribute(
                org.springframework.security.core.Authentication.class.getName());

        DefaultOAuth2TokenContext.Builder contextBuilder = DefaultOAuth2TokenContext.builder()
                .registeredClient(registeredClient)
                .principal(principal != null ? principal : clientPrincipal)
                .authorizationServerContext(AuthorizationServerContextHolder.getContext())
                .authorization(authorization)
                .authorizedScopes(authorizedScopes)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrant(refreshTokenAuthentication);

        // Generate new access token
        OAuth2TokenContext accessTokenContext = contextBuilder
                .tokenType(OAuth2TokenType.ACCESS_TOKEN)
                .build();
        OAuth2Token newAccessToken = tokenGenerator.generate(accessTokenContext);
        if (newAccessToken == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR);
        }

        // Generate new refresh token (rotation enabled by reuseRefreshTokens=false)
        org.springframework.security.oauth2.core.OAuth2AccessToken sasAccessToken =
                new org.springframework.security.oauth2.core.OAuth2AccessToken(
                        org.springframework.security.oauth2.core.OAuth2AccessToken.TokenType.BEARER,
                        newAccessToken.getTokenValue(),
                        newAccessToken.getIssuedAt(),
                        newAccessToken.getExpiresAt(),
                        authorizedScopes);

        OAuth2TokenContext refreshTokenContext = contextBuilder
                .tokenType(OAuth2TokenType.REFRESH_TOKEN)
                .build();
        OAuth2Token newRefreshTokenObj = tokenGenerator.generate(refreshTokenContext);
        if (newRefreshTokenObj == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.SERVER_ERROR);
        }
        OAuth2RefreshToken newRefreshToken = new OAuth2RefreshToken(
                newRefreshTokenObj.getTokenValue(),
                newRefreshTokenObj.getIssuedAt(),
                newRefreshTokenObj.getExpiresAt());

        // --- Update SAS authorization store ---
        OAuth2Authorization updatedAuthorization = OAuth2Authorization.from(authorization)
                .token(sasAccessToken)
                .token(newRefreshToken)
                .build();
        authorizationService.save(updatedAuthorization);

        // --- Persist rotation in domain JPA store ---
        Instant now = Instant.now();
        persistRotation(submittedTokenValue, newRefreshToken, authorization, registeredClient, now);

        // --- Publish auth.token.refreshed event ---
        String accountId = authorization.getPrincipalName();
        String tenantId = extractClientTenantId(registeredClient);
        authEventPublisher.publishTokenRefreshed(
                accountId,
                tenantId != null ? tenantId : "unknown",
                submittedTokenValue,
                newRefreshToken.getTokenValue(),
                buildSessionContext());

        log.debug("SAS refresh_token rotated: account={}, oldJti={}, newJti={}",
                accountId, submittedTokenValue, newRefreshToken.getTokenValue());

        return new OAuth2AccessTokenAuthenticationToken(
                registeredClient, clientPrincipal, sasAccessToken, newRefreshToken,
                Map.of());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2RefreshTokenAuthenticationToken.class.isAssignableFrom(authentication);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private OAuth2ClientAuthenticationToken getAuthenticatedClientElseThrowInvalidClient(
            Authentication authentication) {
        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2ClientAuthenticationToken clientAuth
                && clientAuth.isAuthenticated()) {
            return clientAuth;
        }
        throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
    }

    /**
     * Extracts tenant_id from the registered client.
     *
     * <p>Reads from {@link org.springframework.security.oauth2.server.authorization.settings.ClientSettings}
     * custom keys set by {@link OAuthClientMapper} (Option B, TASK-BE-252).
     * Falls back to the legacy {@code clientName = "tenantId|tenantType"} format
     * for RegisteredClient instances not originating from the JPA mapper.
     */
    private String extractClientTenantId(RegisteredClient client) {
        Object tenantId = client.getClientSettings().getSetting(OAuthClientMapper.SETTING_TENANT_ID);
        if (tenantId instanceof String tid && !tid.isBlank()) {
            return tid;
        }
        // Legacy fallback: clientName = "tenantId|tenantType"
        String clientName = client.getClientName();
        if (clientName != null && clientName.contains("|")) {
            return clientName.split("\\|", 2)[0].trim();
        }
        return null;
    }

    /**
     * Persists the new refresh token in the domain JPA store and revokes the old one.
     * Connects the SAS token value to the domain store via the {@code jti} field.
     */
    private void persistRotation(String oldTokenValue, OAuth2RefreshToken newRefreshToken,
                                  OAuth2Authorization authorization, RegisteredClient registeredClient,
                                  Instant now) {
        String accountId = authorization.getPrincipalName();
        String tenantId = extractClientTenantId(registeredClient);
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = "fan-platform"; // fallback per multi-tenancy policy
        }

        Instant expiresAt = newRefreshToken.getExpiresAt() != null
                ? newRefreshToken.getExpiresAt()
                : now.plusSeconds(2592000L); // 30-day fallback

        RefreshToken newDomainToken = RefreshToken.create(
                newRefreshToken.getTokenValue(),
                accountId,
                tenantId,
                now,
                expiresAt,
                oldTokenValue,   // rotated_from = old token value (used as JTI)
                null,            // deviceFingerprint — not available via SAS flow
                null             // deviceId — not available via SAS flow
        );
        refreshTokenRepository.save(newDomainToken);

        // Revoke the old token in the domain store
        refreshTokenRepository.findByJti(oldTokenValue).ifPresent(old -> {
            old.revoke();
            refreshTokenRepository.save(old);
        });
    }

    /**
     * Handles refresh-token reuse: revokes all account sessions, emits security event.
     */
    private void handleReuseDetected(RefreshToken existingToken, String jti,
                                      OAuth2Authorization authorization) {
        String accountId = authorization.getPrincipalName();
        log.warn("SAS_REFRESH: reuse detected for account={}, jti={}", accountId, jti);

        Instant originalRotationAt = refreshTokenRepository.findByRotatedFrom(jti)
                .map(RefreshToken::getIssuedAt)
                .orElse(null);

        boolean alreadyRevoked = existingToken.isRevoked();

        List<DeviceSession> activeSessions = deviceSessionRepository.findActiveByAccountId(accountId);
        java.util.Map<String, List<String>> jtisByDevice = new java.util.LinkedHashMap<>();
        for (DeviceSession session : activeSessions) {
            jtisByDevice.put(session.getDeviceId(),
                    refreshTokenRepository.findActiveJtisByDeviceId(session.getDeviceId()));
        }

        int revokedCount = refreshTokenRepository.revokeAllByAccountId(accountId);
        bulkInvalidationStore.invalidateAll(accountId, 2592000L); // 30-day window

        if (alreadyRevoked && revokedCount == 0) {
            log.info("SAS_REFRESH: reuse on already-revoked token, skip duplicate event. account={}",
                    accountId);
            return;
        }

        Instant reuseAttemptAt = Instant.now();

        // TASK-BE-248 Phase 2b / TASK-BE-259: tenantId from the reused token's DB record
        // (authoritative). Resolved before publishing so it flows into auth.token.reuse.detected.
        String tenantId = existingToken.getTenantId() != null && !existingToken.getTenantId().isBlank()
                ? existingToken.getTenantId()
                : "fan-platform"; // SAS flow default per persistRotation fallback

        authEventPublisher.publishTokenReuseDetected(
                accountId,
                tenantId,
                jti,
                originalRotationAt,
                reuseAttemptAt,
                "masked",    // IP not available in SAS provider context
                "unknown",   // device fingerprint not available
                true,
                revokedCount
        );

        for (DeviceSession session : activeSessions) {
            if (session.isRevoked()) {
                continue;
            }
            List<String> deviceJtis = jtisByDevice.getOrDefault(session.getDeviceId(), List.of());
            session.revoke(reuseAttemptAt, RevokeReason.TOKEN_REUSE);
            deviceSessionRepository.save(session);
            authEventPublisher.publishAuthSessionRevoked(
                    accountId,
                    tenantId,
                    session.getDeviceId(),
                    RevokeReason.TOKEN_REUSE.name(),
                    deviceJtis,
                    reuseAttemptAt,
                    ACTOR_TYPE_SYSTEM,
                    null
            );
        }
    }

    /**
     * Builds a minimal SessionContext-compatible object for event publishing.
     * IP and device info are not available in the SAS provider context.
     */
    private com.example.auth.domain.session.SessionContext buildSessionContext() {
        return new com.example.auth.domain.session.SessionContext(
                "0.0.0.0",  // raw IP (masked below)
                "unknown",
                "unknown",
                null
        );
    }
}
