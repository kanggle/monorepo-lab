package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.application.port.OAuthAuthorizationRevocationPort;
import com.example.auth.domain.repository.BulkInvalidationStore;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.infrastructure.oauth2.persistence.OAuthClientMapper;
import com.example.auth.domain.session.DeviceSession;
import com.example.auth.domain.session.RevokeReason;
import com.example.auth.domain.tenant.TenantContext;
import com.example.auth.domain.token.RefreshToken;
import com.example.auth.domain.token.RotatedTokenReplayPolicy;
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
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
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

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Custom {@link AuthenticationProvider} for the {@code refresh_token} grant type.
 *
 * <p>Replaces SAS's built-in {@code OAuth2RefreshTokenAuthenticationProvider} to integrate
 * refresh-token rotation and reuse-detection with the existing domain infrastructure
 * (TASK-BE-604: "replaces" literally — the built-in provider is removed from the token
 * endpoint, so an {@code invalid_grant} raised here is the final answer):
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
 *   <li>Replay of an already-rotated token (TASK-BE-606) — from the mirror store's rotation
 *       chain, BEFORE the SAS lookup (SAS no longer knows a rotated-away token). Within the
 *       grace window → {@code invalid_grant}, nothing revoked; otherwise the family is revoked
 *       and {@code auth.token.reuse.detected} emitted ({@link RotatedTokenReplayPolicy})</li>
 *   <li>Reuse detection — runs first (security-critical, same ordering as legacy RefreshTokenUseCase)</li>
 *   <li>Expired/revoked check</li>
 *   <li>Tenant mismatch check — mirror row tenant vs the session's login-time tenant
 *       (TASK-BE-604), not the client's</li>
 *   <li>Rotation: generate new tokens, persist new JPA record, revoke old JPA record</li>
 *   <li>Event publishing (auth.token.refreshed or auth.token.reuse.detected)</li>
 * </ol>
 *
 * <p>TASK-BE-251 Phase 2b — SAS refresh_token grant integration.
 */
@Slf4j
public class SasRefreshTokenAuthenticationProvider implements AuthenticationProvider {

    private static final String ACTOR_TYPE_SYSTEM = "SYSTEM";

    /**
     * TASK-BE-606: upper bound on mirror rows visited while looking for the SAS authorization
     * that holds the head of a reused token's chain ({@code findFamilyAuthorization}).
     */
    private static final int MAX_FAMILY_WALK = 64;

    /**
     * Token type SAS uses for the OIDC ID token. It is not one of the
     * {@link OAuth2TokenType} constants — SAS's own providers build it from
     * {@link OidcParameterNames#ID_TOKEN}, and {@code JwtGenerator} /
     * {@code TenantClaimTokenCustomizer} both branch on this exact string.
     *
     * <p>TASK-MONO-705 (owner decision ⓐ).
     */
    private static final OAuth2TokenType ID_TOKEN_TOKEN_TYPE =
            new OAuth2TokenType(OidcParameterNames.ID_TOKEN);

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
    /**
     * TASK-BE-606: closes the account's SAS authorizations on reuse. The mirror-row revoke alone
     * misses a session whose mirror row is keyed by the login email (written before TASK-BE-603)
     * or whose initial-issuance mirror INSERT was swallowed — the authorization is the one store
     * every session has.
     */
    private final OAuthAuthorizationRevocationPort authorizationRevocationPort;
    /** TASK-BE-606: rotated-token replay classification (grace window vs reuse). */
    private final RotatedTokenReplayPolicy replayPolicy;
    private final Clock clock;

    public SasRefreshTokenAuthenticationProvider(
            OAuth2AuthorizationService authorizationService,
            OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator,
            RefreshTokenRepository refreshTokenRepository,
            TokenReuseDetector tokenReuseDetector,
            BulkInvalidationStore bulkInvalidationStore,
            DeviceSessionRepository deviceSessionRepository,
            AuthEventPublisher authEventPublisher,
            PlatformTransactionManager transactionManager,
            OAuthAuthorizationRevocationPort authorizationRevocationPort,
            RotatedTokenReplayPolicy replayPolicy,
            Clock clock) {
        this.authorizationService = authorizationService;
        this.tokenGenerator = tokenGenerator;
        this.refreshTokenRepository = refreshTokenRepository;
        this.tokenReuseDetector = tokenReuseDetector;
        this.bulkInvalidationStore = bulkInvalidationStore;
        this.deviceSessionRepository = deviceSessionRepository;
        this.authEventPublisher = authEventPublisher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.authorizationRevocationPort = authorizationRevocationPort;
        this.replayPolicy = replayPolicy;
        this.clock = clock;
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
        Instant requestTime = clock.instant();

        // --- SECURITY (TASK-BE-606): replay of an already-rotated token — BEFORE findByToken ---
        // SAS keeps only the authorization's CURRENT refresh token, so findByToken(rotated) is
        // null and the reuse branch further down was unreachable for the one shape reuse
        // detection exists for (identity-platform.md § Refresh Token: reuse → revoke the family).
        // The mirror store's rotation chain still remembers the token; ask it first.
        RotatedTokenReplayPolicy.Assessment replay =
                replayPolicy.assess(submittedTokenValue, requestTime);
        if (replay.verdict() != RotatedTokenReplayPolicy.Verdict.NOT_ROTATED) {
            throw rejectReplay(replay, submittedTokenValue, null, null, requestTime);
        }

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

        // TASK-BE-604: the tenant this session was authenticated in — the tenant check below,
        // the rotated mirror row and the auth.token.refreshed event all use it.
        String clientTenant = extractClientTenantId(registeredClient);
        String sessionTenant = AuthorizationSessionTenant.of(authorization, clientTenant);

        // --- SECURITY: Reuse detection (runs first, fail-closed) ---
        Optional<RefreshToken> existingDomainTokenOpt =
                refreshTokenRepository.findByJti(submittedTokenValue);

        if (existingDomainTokenOpt.isPresent()) {
            RefreshToken existingDomainToken = existingDomainTokenOpt.get();

            if (tokenReuseDetector.isReuse(existingDomainToken)) {
                // A child appeared between the replay check above and here — a concurrent
                // refresh of this same token committed in between. Classify it with the SAME
                // policy, so the race gets the same grace window as the pre-check (and the
                // reuse path runs exactly once per request — never both).
                // (NOT_ROTATED here can only mean the child differs from this token in letter
                // case alone — the column compares case-insensitively; not this token's child.)
                RotatedTokenReplayPolicy.Assessment inFlight =
                        replayPolicy.assess(submittedTokenValue, requestTime);
                if (inFlight.verdict() != RotatedTokenReplayPolicy.Verdict.NOT_ROTATED) {
                    throw rejectReplay(inFlight, submittedTokenValue, authorization,
                            existingDomainToken.getTenantId(), requestTime);
                }
            }

            // Revoked check
            if (existingDomainToken.isRevoked()) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
            }

            // Expired check
            if (existingDomainToken.isExpired()) {
                throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
            }

            // Cross-tenant check — TASK-BE-604 (owner decision D/A): the mirror row's tenant must
            // match the tenant the SESSION was authenticated in (the login-time principal's
            // tenant = the tenant_id claim this refresh is about to mint again), NOT the
            // registered client's tenant. The two differ for a console session of an operator
            // whose credential lives in a consumer tenant; comparing with the client tenant
            // refused every such refresh, which went unnoticed only while SAS's built-in
            // provider re-ran the grant after this one (now removed — AuthorizationServerConfig).
            if (sessionTenant != null && !sessionTenant.isBlank()) {
                String tokenTenant = existingDomainToken.getTenantId();
                if (!sessionTenant.equals(tokenTenant)) {
                    // The refresh-token value is deliberately not logged (identity-platform:
                    // never log refresh tokens); the event below carries it as reusedJti.
                    log.warn("SAS_REFRESH: cross-tenant attempt detected. "
                                    + "sessionTenant={}, clientTenant={}, tokenTenant={}",
                            sessionTenant, clientTenant, tokenTenant);
                    authEventPublisher.publishTokenTenantMismatch(
                            AuthorizationAccountId.forMirrorRow(authorization),
                            tokenTenant, sessionTenant,
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
        // persisted (rare race case), or its initial-issuance INSERT failed (swallowed by
        // DomainSyncOAuth2AuthorizationService). SAS's own expiry check already ran above, and
        // persistRotation below writes the row this rotation continues from.
        //
        // TASK-BE-604: every rejection above is FINAL. SAS's built-in
        // OAuth2RefreshTokenAuthenticationProvider is removed from the token endpoint
        // (AuthorizationServerConfig#removeBuiltInRefreshTokenProvider), so ProviderManager has
        // no other refresh_token provider to retry the grant with.

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
        OAuth2Authorization.Builder authorizationBuilder = OAuth2Authorization.from(authorization)
                .token(sasAccessToken)
                .token(newRefreshToken);

        // --- Generate a new ID token when the authorization carries `openid` ---
        //
        // TASK-MONO-705 (owner decision ⓐ, 2026-09-18). This provider used to return
        // `Map.of()` as additionalParameters, so the rotated response carried NO
        // `id_token` — while `auth-api.md` § POST /oauth2/token already promised
        // *"id_token: string (scope=openid 포함 시)"* for this endpoint. The console
        // only re-sets its `console_id_token` cookie when the response contains one
        // (`session-refresh.ts`), so ~30 minutes after login the cookie was simply
        // gone, and every logout from then on fell back to a LOCAL logout with no
        // `id_token_hint` — the IdP session survived it.
        //
        // 🔴 Measured, not assumed (2026-09-18 demo window): with the IdP session
        // alive and only `console_id_token` removed, logging out and pressing
        // "log in" again re-entered WITHOUT a password. See the ticket's control
        // group — the 31-minute observation looked harmless only because the IAM
        // browser session had expired on its own by then.
        //
        // This mirrors SAS's built-in OAuth2RefreshTokenAuthenticationProvider:
        // the ID token is generated from a context that already sees the NEW access
        // and refresh tokens, and it is stored ON the authorization. 🔴 Storing it
        // is not cosmetic — `OidcLogoutAuthenticationProvider` resolves the
        // authorization by `findByToken(idTokenHint, ID_TOKEN)`, so an ID token that
        // is handed out but not stored would fail RP-initiated logout, which is the
        // very thing this change exists to restore.
        OidcIdToken idToken = null;
        if (authorizedScopes.contains(OidcScopes.OPENID)) {
            OAuth2TokenContext idTokenContext = contextBuilder
                    .tokenType(ID_TOKEN_TOKEN_TYPE)
                    .authorization(authorizationBuilder.build())
                    .build();
            OAuth2Token generatedIdToken = tokenGenerator.generate(idTokenContext);
            if (!(generatedIdToken instanceof Jwt jwt)) {
                // 🔴 Fail loudly rather than silently dropping the ID token again —
                // a silent drop is exactly the defect this block repairs.
                throw new OAuth2AuthenticationException(new OAuth2Error(
                        OAuth2ErrorCodes.SERVER_ERROR,
                        "The token generator failed to generate the ID token.",
                        null));
            }
            idToken = new OidcIdToken(
                    jwt.getTokenValue(), jwt.getIssuedAt(), jwt.getExpiresAt(), jwt.getClaims());
            OidcIdToken issuedIdToken = idToken;
            authorizationBuilder.token(issuedIdToken, metadata -> metadata.put(
                    OAuth2Authorization.Token.CLAIMS_METADATA_NAME, issuedIdToken.getClaims()));
        }

        OAuth2Authorization updatedAuthorization = authorizationBuilder.build();

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
                // TASK-BE-606: the child's issued_at is what the replay grace window is measured
                // from — take it from the same clock the window is checked against.
                Instant now = clock.instant();
                persistRotation(submittedTokenValue, newRefreshToken, authorization, sessionTenant, now);

                // --- Publish auth.token.refreshed event (outbox row INSERT) ---
                // OutboxWriter.save() requires an active EntityManager transaction.
                // Placement INSIDE the same transaction as persistRotation() is the
                // standard outbox pattern: the outbox row is committed atomically
                // with the rotation. This is NOT the cycle 8 anti-pattern (cycle 8
                // added @Transactional via AOP — declarative — which collided with
                // the dual-INSERT race that wasn't yet resolved). Here the race is
                // resolved (skip-path) and we are using a programmatic
                // TransactionTemplate, not annotation-based AOP.
                // TASK-BE-603: the account UUID, not the principal name (the login email).
                String accountId = AuthorizationAccountId.forMirrorRow(authorization);
                // TASK-BE-604: auth-events.md defines tenantId as "the token's tenant_id" — the
                // session tenant, which differs from the client's for a cross-tenant session.
                authEventPublisher.publishTokenRefreshed(
                        accountId,
                        sessionTenant != null ? sessionTenant : "unknown",
                        submittedTokenValue,
                        newRefreshToken.getTokenValue(),
                        buildSessionContext());

                // Token values are deliberately not logged (identity-platform: never log refresh
                // tokens — TASK-BE-606 removed them from this line).
                log.debug("SAS refresh_token rotated: account={}", accountId);
            } catch (RuntimeException ex) {
                // Ensure the flag is released immediately on failure; afterCompletion
                // will fire too but unbindRotationFlagIfBound() is idempotent.
                unbindRotationFlagIfBound();
                status.setRollbackOnly();
                throw ex;
            }
        });

        // TASK-MONO-705 ⓐ: hand the ID token back on the response. Without `openid`
        // in the authorized scopes there is nothing to hand back and the map stays
        // empty — a non-OIDC client must not suddenly start receiving an ID token.
        Map<String, Object> additionalParameters = idToken != null
                ? Map.of(OidcParameterNames.ID_TOKEN, idToken.getTokenValue())
                : Map.of();

        return new OAuth2AccessTokenAuthenticationToken(
                registeredClient, clientPrincipal, sasAccessToken, newRefreshToken,
                additionalParameters);
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
     *
     * <p>TASK-BE-604: the new row carries the SESSION tenant ({@link AuthorizationSessionTenant})
     * — the same tenant the first row got from the token's {@code tenant_id} claim at issuance.
     * It used to carry the client's tenant, so for a cross-tenant session the first row and the
     * rotated rows disagreed, and a session whose first row was missing got a rotated row that
     * its own next refresh would refuse.
     */
    private void persistRotation(String oldTokenValue, OAuth2RefreshToken newRefreshToken,
                                  OAuth2Authorization authorization, String sessionTenant,
                                  Instant now) {
        // TASK-BE-603: refresh_tokens.account_id is the account UUID (VARCHAR(36)). The
        // principal name is the login email — writing it here made every refresh of an
        // account whose email exceeds 36 characters fail on this INSERT.
        String accountId = AuthorizationAccountId.forMirrorRow(authorization);
        String tenantId = sessionTenant;
        if (tenantId == null || tenantId.isBlank()) {
            tenantId = TenantContext.DEFAULT_TENANT_ID; // fallback per multi-tenancy policy
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
     * TASK-BE-606 — answers a request whose token the mirror store shows as already rotated.
     * Always returns the exception to throw; for {@link RotatedTokenReplayPolicy.Verdict#REUSE}
     * it first revokes the family ({@link #handleReuse}).
     *
     * @param knownAuthorization the SAS authorization when the caller already holds it (the
     *                           in-flow race path), {@code null} on the pre-check path — a
     *                           rotated-away token has no authorization of its own
     * @param reusedRowTenant    the submitted token's own mirror-row tenant when known
     */
    private OAuth2AuthenticationException rejectReplay(RotatedTokenReplayPolicy.Assessment assessment,
                                                       String submittedTokenValue,
                                                       OAuth2Authorization knownAuthorization,
                                                       String reusedRowTenant,
                                                       Instant requestTime) {
        RefreshToken original = assessment.originalRotation();
        if (assessment.verdict() == RotatedTokenReplayPolicy.Verdict.WITHIN_GRACE) {
            // A client race (two tabs, a retried request) — refuse without revoking anything,
            // so the request that won keeps its session. Not an event: the owner decision
            // (2026-09-26) keeps this out of the security pipeline. Token values not logged.
            log.info("SAS_REFRESH: replay of a refresh token rotated {} ago, within the {}s grace "
                            + "window — refused, nothing revoked. account={}",
                    java.time.Duration.between(original.getIssuedAt(), requestTime),
                    replayPolicy.grace().toSeconds(), original.getAccountId());
            return new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_GRANT);
        }

        handleReuse(submittedTokenValue, assessment, knownAuthorization, reusedRowTenant, requestTime);
        return new OAuth2AuthenticationException(new OAuth2Error(
                OAuth2ErrorCodes.INVALID_GRANT,
                "Refresh token reuse detected — all sessions have been revoked.",
                null));
    }

    /**
     * Handles refresh-token reuse: revokes the whole refresh-token family of the account —
     * every mirror row, every device session, AND every SAS authorization — and emits
     * {@code auth.token.reuse.detected} (identity-platform.md § Refresh Token, reuse detection).
     *
     * <p>TASK-BE-274: the JPA write path ({@code revokeAllByAccountId} + per-device
     * {@code deviceSessionRepository.save}) is wrapped in a programmatic
     * transaction. Reads (lookups + collecting jtisByDevice) and side-effects
     * (Redis bulk invalidation + Kafka event publish) stay outside so a Kafka
     * outage cannot roll back the security-critical revoke. Mirrors the cycle 8
     * negative lesson of PR #264 (don't put publish* under @Transactional).
     *
     * <p><b>Whose family (TASK-BE-606).</b> On the pre-check path the submitted token no longer
     * has an authorization, so the owner is resolved from the chain: the SAS authorization that
     * currently holds a head of the chain ({@link #findFamilyAuthorization}) gives the account
     * UUID and — for the TASK-BE-603 drain window — the principal name that email-keyed mirror
     * rows carry. When no head is held any more (every branch already closed), the earliest
     * child's own {@code account_id} is used: that is the UUID for every row written since
     * TASK-BE-603; a pre-BE-603 row carries the email, which still reaches that row's email-keyed
     * siblings through {@code revokeAllByAccountId}, while the SAS port finds no credential for
     * it and closes nothing (those authorizations have expired or were closed already, or a head
     * would have been found).
     *
     * <p><b>Nothing revoked ⇒ no event.</b> When the bulk revoke, the device sessions and the
     * SAS port together close nothing, the family was already dead (a previous detection,
     * logout, force-logout) and a replay of it is not announced again — the same rule as
     * before TASK-BE-606 ("already revoked and nothing revoked"), and the reason the second
     * reuse event that locks the account (security-service {@code TokenReuseRule}) has to come
     * from a replay that actually found live sessions.
     */
    private void handleReuse(String reusedToken, RotatedTokenReplayPolicy.Assessment assessment,
                             OAuth2Authorization knownAuthorization, String reusedRowTenant,
                             Instant reuseAttemptAt) {
        RefreshToken original = assessment.originalRotation();
        OAuth2Authorization familyAuthorization = knownAuthorization != null
                ? knownAuthorization
                : findFamilyAuthorization(assessment.children());

        // TASK-BE-603: key everything below — the bulk revoke, the device-session lookup,
        // the invalidate-all marker and the events — on the account UUID. With the principal
        // name (the login email) the device-session lookup and the Redis marker were keyed on
        // a value no other path uses, and the events carried the email as accountId.
        final String accountId;
        // Drain window: mirror rows written before TASK-BE-603 are keyed by the principal
        // name. Revoke those too, so the mirror store keeps covering the same rows it did
        // before this change. Once the last such row has expired (refresh TTL, 30 days) the
        // second UPDATE matches nothing.
        // TASK-BE-604: these UPDATEs are what refuse the other sessions' next refresh — the
        // revoked-row branch above is final now that SAS's built-in refresh provider is gone.
        final String legacyMirrorKey;
        if (familyAuthorization != null) {
            accountId = AuthorizationAccountId.forMirrorRow(familyAuthorization);
            legacyMirrorKey = familyAuthorization.getPrincipalName();
        } else {
            accountId = original.getAccountId();
            legacyMirrorKey = null;
        }
        // Token values are deliberately not logged (identity-platform: never log refresh
        // tokens); the event below carries it as reusedJti, as the contract defines.
        log.warn("SAS_REFRESH: reuse detected — revoking the refresh-token family. account={}, "
                + "children={}", accountId, assessment.children().size());

        Instant originalRotationAt = original != null ? original.getIssuedAt() : null;

        List<DeviceSession> activeSessions = deviceSessionRepository.findActiveByAccountId(accountId);
        java.util.Map<String, List<String>> jtisByDevice = new java.util.LinkedHashMap<>();
        for (DeviceSession session : activeSessions) {
            jtisByDevice.put(session.getDeviceId(),
                    refreshTokenRepository.findActiveJtisByDeviceId(session.getDeviceId()));
        }

        // TASK-BE-248 Phase 2b / TASK-BE-259: tenantId from the reused token's DB record
        // (authoritative). TASK-BE-606: on the pre-check path the reused token's own row may be
        // missing (swallowed initial INSERT), so fall back to the child's — persistRotation
        // writes the session tenant on every row of one chain.
        String candidateTenant = reusedRowTenant;
        if ((candidateTenant == null || candidateTenant.isBlank()) && original != null) {
            candidateTenant = original.getTenantId();
        }
        final String tenantId = candidateTenant != null && !candidateTenant.isBlank()
                ? candidateTenant
                : TenantContext.DEFAULT_TENANT_ID; // SAS flow default per persistRotation fallback

        // Transactional revoke + outbox publish — all DB writes share one tx so
        // (a) the @Modifying bulk update has an active EntityManager and
        // (b) the outbox rows for token-reuse and per-device session-revoked
        //     events commit atomically with the revoke (standard outbox pattern).
        // Redis bulk invalidation is intentionally OUTSIDE the tx so a Redis
        // outage cannot roll back the security-critical revoke.
        Integer revokedCountBoxed = transactionTemplate.execute(status -> {
            int rc = doRevokeAllForReuse(accountId, legacyMirrorKey, activeSessions, reuseAttemptAt);
            // TASK-BE-606: close the SAS authorizations too. The mirror-row UPDATE refuses the
            // next refresh of every session whose mirror row it reached (BE-604); this reaches
            // the sessions it cannot — a mirror row keyed by the login email, or one whose
            // initial INSERT was swallowed — and also stops their access tokens introspecting
            // as active. It joins this transaction (JDBC + authorizationService.save).
            rc += authorizationRevocationPort.revokeActiveRefreshTokens(accountId);

            // Nothing left to revoke = the family was already closed; see the method comment.
            if (rc == 0) {
                return rc;
            }

            authEventPublisher.publishTokenReuseDetected(
                    accountId, tenantId, reusedToken, originalRotationAt, reuseAttemptAt,
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

        if (revokedCount == 0) {
            log.info("SAS_REFRESH: reuse of an already-closed refresh-token family, "
                    + "no event. account={}", accountId);
        }
    }

    /**
     * TASK-BE-606 — the SAS authorization that currently holds a head of the rotation chain
     * below the reused token, or {@code null}.
     *
     * <p>Only a head (a row nothing was rotated from) can be held by an authorization — SAS
     * keeps the current refresh token only. Walks the chain breadth-first, bounded by
     * {@link #MAX_FAMILY_WALK} rows so a replay of a very old token cannot turn one request
     * into an unbounded number of queries; past the bound the caller falls back to the
     * mirror row's own {@code account_id}.
     */
    private OAuth2Authorization findFamilyAuthorization(List<RefreshToken> children) {
        java.util.Deque<RefreshToken> frontier = new java.util.ArrayDeque<>(children);
        int visited = 0;
        while (!frontier.isEmpty() && visited < MAX_FAMILY_WALK) {
            RefreshToken node = frontier.poll();
            visited++;
            List<RefreshToken> next = refreshTokenRepository.findAllByRotatedFrom(node.getJti()).stream()
                    .filter(row -> node.getJti().equals(row.getRotatedFrom()))
                    .toList();
            if (next.isEmpty()) {
                OAuth2Authorization held = authorizationService.findByToken(
                        node.getJti(), OAuth2TokenType.REFRESH_TOKEN);
                if (held != null) {
                    return held;
                }
            } else {
                frontier.addAll(next);
            }
        }
        return null;
    }

    /**
     * Bulk revoke + per-device session revoke under one tx — kept package-private
     * so it can be exercised by unit tests as a single atomic step.
     *
     * <p>Returns the count from {@link RefreshTokenRepository#revokeAllByAccountId(String)}
     * — for the account UUID plus, during the TASK-BE-603 drain window, for the legacy
     * principal-name key when it differs.
     */
    private int doRevokeAllForReuse(String accountId, String legacyMirrorKey,
                                     List<DeviceSession> activeSessions,
                                     Instant reuseAttemptAt) {
        int revokedCount = refreshTokenRepository.revokeAllByAccountId(accountId);
        if (legacyMirrorKey != null && !legacyMirrorKey.isBlank()
                && !legacyMirrorKey.equals(accountId)) {
            revokedCount += refreshTokenRepository.revokeAllByAccountId(legacyMirrorKey);
        }
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
