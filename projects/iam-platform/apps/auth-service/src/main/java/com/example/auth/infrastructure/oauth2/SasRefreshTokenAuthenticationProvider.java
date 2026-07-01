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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

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
    /**
     * TASK-BE-274: programmatic transaction template used to wrap the rotation
     * write path (SAS save + persistRotation) and the reuse-detected write path
     * (revokeAllByAccountId + bulkInvalidation + per-device revoke).
     *
     * <p>Required because the SAS {@code OAuth2TokenEndpointFilter} does not wrap
     * provider invocations in a Spring-managed transaction, and the provider is
     * instantiated manually in {@link AuthorizationServerConfig} (so the AOP
     * {@code @Transactional} interceptor is not applied — A3 anti-pattern). Using
     * a programmatic {@link TransactionTemplate} avoids the AOP dependency while
     * keeping {@link AuthEventPublisher#publishTokenRefreshed} (and other event
     * publishers) outside the DB transaction — that placement was the explicit
     * negative lesson of PR #264 cycle 8.
     */
    private final TransactionTemplate transactionTemplate;

    public SasRefreshTokenAuthenticationProvider(
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            RefreshTokenRepository refreshTokenRepository,
            TokenReuseDetector tokenReuseDetector,
            BulkInvalidationStore bulkInvalidationStore,
            DeviceSessionRepository deviceSessionRepository,
            AuthEventPublisher authEventPublisher,
            PlatformTransactionManager transactionManager) {
        this.authorizationService = authorizationService;
        this.tokenGenerator = tokenGenerator;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenReuseDetector = tokenReuseDetector;
        this.bulkInvalidationStore = bulkInvalidationStore;
        this.deviceSessionRepository = deviceSessionRepository;
        this.authEventPublisher = authEventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
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
        // TASK-BE-465: recover the ORIGINAL resource-owner Authentication that SAS
        // stored on the authorization at authorization_code time. SAS keys it under
        // `Principal.class.getName()` ("java.security.Principal") — the exact key its
        // own OAuth2AuthorizationCodeAuthenticationProvider writes and the built-in
        // refresh provider reads. Reading it under
        // `Authentication.class.getName()` ("org.springframework.security.core.Authentication")
        // ALWAYS returned null, so every refresh fell back to `clientPrincipal` below
        // → the rotated access token was minted with `sub` = client_id and `roles` =
        // RoleSeedPolicy default (the account_id/tenant/roles carried on the stored
        // principal's `details` map were lost). Downstream gateways bind
        // `X-User-Id ← sub` as a UUID, so a client_id `sub` broke every authenticated
        // call (e.g. user-service 400) ~5 min after login, on the first token refresh.
        Authentication principal = authorization.getAttribute(
                java.security.Principal.class.getName());

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

        // --- Update SAS authorization store + persist rotation in domain JPA store ---
        // TASK-BE-274 / ADR-003 옵션 B: bind a TSM resource flag so that
        // DomainSyncOAuth2AuthorizationService.syncRefreshTokenToDomainStore() skips
        // its INSERT during this rotation. The provider's own persistRotation() below
        // is the single source of truth for the new refresh_tokens row, eliminating
        // the A2 dual-INSERT race on idx_rt_jti.
        //
        // Cleanup strategy (defence-in-depth):
        //   1. afterCompletion synchronization unbinds when the outer transaction
        //      ends (commit, rollback, unknown). Guarantees release even on the
        //      success path.
        //   2. try-finally fail-safe unbind covers the rare case where this method
        //      runs without an active synchronization (e.g. unit-test contexts) so
        //      the static TSM never carries a stale flag into the next call on the
        //      same thread.
        OAuth2Authorization updatedAuthorization = OAuth2Authorization.from(authorization)
                .token(sasAccessToken)
                .token(newRefreshToken)
                .build();

        // Wrap the dual-write (SAS save + domain persistRotation) in a programmatic
        // transaction so the JPA save() inside persistRotation() has an active
        // EntityManager transaction (REQUIRED + new). Inside the callback, bind
        // the SAS_ROTATION_SKIP_KEY so DomainSyncOAuth2AuthorizationService
        // skips its INSERT (provider owns the row). Cleanup runs in afterCompletion
        // (registered while a synchronization is active inside the template) plus
        // a finally block as fail-safe.
        transactionTemplate.executeWithoutResult(status -> {
            // Defensive: if a previous invocation on the same thread leaked the flag
            // (should never happen with the afterCompletion + finally pair but guards
            // against future regressions), unbind it before re-binding.
            unbindRotationFlagIfBound();
            TransactionSynchronizationManager.bindResource(
                    DomainSyncOAuth2AuthorizationService.SAS_ROTATION_SKIP_KEY, Boolean.TRUE);
            // The TransactionTemplate guarantees synchronization is active inside this
            // callback, so afterCompletion is a reliable cleanup hook.
            TransactionSynchronizationManager.registerSynchronization(
                    new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int s) {
                            unbindRotationFlagIfBound();
                        }
                    });
            try {
                authorizationService.save(updatedAuthorization);

                // --- Persist rotation in domain JPA store ---
                Instant now = Instant.now();
                persistRotation(submittedTokenValue, newRefreshToken, authorization, registeredClient, now);

                // --- Publish auth.token.refreshed event (outbox row INSERT) ---
                // OutboxWriter.save() requires an active EntityManager transaction.
                // Placement INSIDE the same transaction as persistRotation() is the
                // standard outbox pattern: the outbox row is committed atomically
                // with the rotation. This is NOT the cycle 8 anti-pattern (cycle 8
                // added @Transactional via AOP — declarative — which collided with
                // the dual-INSERT race that wasn't yet resolved). Here the race is
                // resolved (skip-path) and we are using a programmatic
                // TransactionTemplate, not annotation-based AOP.
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
            } catch (RuntimeException ex) {
                // Ensure the flag is released immediately on failure; afterCompletion
                // will fire too but unbindRotationFlagIfBound() is idempotent.
                unbindRotationFlagIfBound();
                status.setRollbackOnly();
                throw ex;
            }
        });

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

    /**
     * Releases the {@link DomainSyncOAuth2AuthorizationService#SAS_ROTATION_SKIP_KEY}
     * resource on the current thread if it is currently bound. Idempotent.
     *
     * <p>TASK-BE-274 / ADR-003 옵션 B: invoked from both the in-method finally block
     * (no-active-synchronization fallback) and the {@code afterCompletion} hook
     * registered in {@link #authenticate} (active-transaction primary path).
     */
    private static void unbindRotationFlagIfBound() {
        if (TransactionSynchronizationManager.hasResource(
                DomainSyncOAuth2AuthorizationService.SAS_ROTATION_SKIP_KEY)) {
            TransactionSynchronizationManager.unbindResource(
                    DomainSyncOAuth2AuthorizationService.SAS_ROTATION_SKIP_KEY);
        }
    }

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
     *
     * <p>TASK-BE-274: the JPA write path ({@code revokeAllByAccountId} + per-device
     * {@code deviceSessionRepository.save}) is wrapped in a programmatic
     * transaction. Reads (lookups + collecting jtisByDevice) and side-effects
     * (Redis bulk invalidation + Kafka event publish) stay outside so a Kafka
     * outage cannot roll back the security-critical revoke. Mirrors the cycle 8
     * negative lesson of PR #264 (don't put publish* under @Transactional).
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

        // TASK-BE-248 Phase 2b / TASK-BE-259: tenantId from the reused token's DB record
        // (authoritative). Resolved before publishing so it flows into auth.token.reuse.detected.
        final String tenantId = existingToken.getTenantId() != null && !existingToken.getTenantId().isBlank()
                ? existingToken.getTenantId()
                : "fan-platform"; // SAS flow default per persistRotation fallback

        // Transactional revoke + outbox publish — all DB writes share one tx so
        // (a) the @Modifying bulk update has an active EntityManager and
        // (b) the outbox rows for token-reuse and per-device session-revoked
        //     events commit atomically with the revoke (standard outbox pattern).
        // Redis bulk invalidation is intentionally OUTSIDE the tx so a Redis
        // outage cannot roll back the security-critical revoke.
        Instant reuseAttemptAt = Instant.now();
        Integer revokedCountBoxed = transactionTemplate.execute(status -> {
            int rc = doRevokeAllForReuse(accountId, activeSessions, reuseAttemptAt);

            // Skip event emission if there was nothing to revoke (already-revoked
            // duplicate-reuse case). Mirrors the prior behaviour.
            if (alreadyRevoked && rc == 0) {
                return rc;
            }

            authEventPublisher.publishTokenReuseDetected(
                    accountId, tenantId, jti, originalRotationAt, reuseAttemptAt,
                    "masked", "unknown", true, rc);

            for (DeviceSession session : activeSessions) {
                if (session.isRevoked()) {
                    continue;
                }
                List<String> deviceJtis = jtisByDevice.getOrDefault(session.getDeviceId(), List.of());
                authEventPublisher.publishAuthSessionRevoked(
                        accountId, tenantId, session.getDeviceId(),
                        RevokeReason.TOKEN_REUSE.name(), deviceJtis, reuseAttemptAt,
                        ACTOR_TYPE_SYSTEM, null);
            }
            return rc;
        });
        int revokedCount = revokedCountBoxed != null ? revokedCountBoxed : 0;

        // Outside the DB transaction — Redis side effect must not gate the
        // committed security revoke.
        bulkInvalidationStore.invalidateAll(accountId, 2592000L); // 30-day window

        if (alreadyRevoked && revokedCount == 0) {
            log.info("SAS_REFRESH: reuse on already-revoked token, skip duplicate event. account={}",
                    accountId);
        }
    }

    /**
     * Bulk revoke + per-device session revoke under one tx — kept package-private
     * so it can be exercised by unit tests as a single atomic step.
     *
     * <p>Returns the count from {@link RefreshTokenRepository#revokeAllByAccountId(String)}.
     */
    private int doRevokeAllForReuse(String accountId, List<DeviceSession> activeSessions,
                                     Instant reuseAttemptAt) {
        int revokedCount = refreshTokenRepository.revokeAllByAccountId(accountId);
        for (DeviceSession session : activeSessions) {
            if (session.isRevoked()) {
                continue;
            }
            session.revoke(reuseAttemptAt, RevokeReason.TOKEN_REUSE);
            deviceSessionRepository.save(session);
        }
        return revokedCount;
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
