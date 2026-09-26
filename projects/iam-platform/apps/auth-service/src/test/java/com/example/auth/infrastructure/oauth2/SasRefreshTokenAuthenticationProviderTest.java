package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.application.port.OAuthAuthorizationRevocationPort;
import com.example.auth.domain.repository.BulkInvalidationStore;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.token.RefreshToken;
import com.example.auth.domain.token.RotatedTokenReplayPolicy;
import com.example.auth.domain.token.TokenReuseDetector;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2AccessTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link SasRefreshTokenAuthenticationProvider}.
 *
 * <p>Validates the security-critical paths:
 * <ul>
 *   <li>Reuse detection triggers {@code invalid_grant} and revokes all tokens</li>
 *   <li>Unknown token (not in SAS store) triggers {@code invalid_grant}</li>
 *   <li>Client not supporting refresh_token grant → {@code unauthorized_client}</li>
 *   <li>{@link #supports(Class)} returns true only for
 *       {@link OAuth2RefreshTokenAuthenticationToken}</li>
 * </ul>
 *
 * <p>TASK-BE-251 Phase 2b.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class SasRefreshTokenAuthenticationProviderTest {

    @Mock
    private OAuth2AuthorizationService authorizationService;
    @Mock
    private OAuth2TokenGenerator<? extends OAuth2Token> tokenGenerator;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private TokenReuseDetector tokenReuseDetector;
    @Mock
    private BulkInvalidationStore bulkInvalidationStore;
    @Mock
    private DeviceSessionRepository deviceSessionRepository;
    @Mock
    private AuthEventPublisher authEventPublisher;
    @Mock
    private PlatformTransactionManager transactionManager;
    @Mock
    private OAuthAuthorizationRevocationPort authorizationRevocationPort;

    /** TASK-BE-606: the request time every test runs at (fixed clock). */
    private static final Instant NOW = Instant.parse("2026-09-26T03:00:00Z");
    private static final Duration GRACE = Duration.ofSeconds(30);

    private SasRefreshTokenAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        // Stub TransactionTemplate.execute(...) plumbing — return a dummy
        // TransactionStatus and let the callback run inline. Lenient because
        // the unauthenticated-client / unknown-client / unauthorized-client
        // / token-not-found tests short-circuit before reaching the template.
        org.mockito.Mockito.lenient().when(transactionManager.getTransaction(any()))
                .thenReturn(new SimpleTransactionStatus());
        org.mockito.Mockito.lenient().doNothing().when(transactionManager).commit(any(TransactionStatus.class));
        org.mockito.Mockito.lenient().doNothing().when(transactionManager).rollback(any(TransactionStatus.class));

        provider = new SasRefreshTokenAuthenticationProvider(
                authorizationService,
                tokenGenerator,
                refreshTokenRepository,
                tokenReuseDetector,
                bulkInvalidationStore,
                deviceSessionRepository,
                authEventPublisher,
                transactionManager,
                authorizationRevocationPort,
                new RotatedTokenReplayPolicy(refreshTokenRepository, GRACE),
                Clock.fixed(NOW, ZoneOffset.UTC));

        // SAS AuthorizationServerContextHolder is a ThreadLocal — set a minimal context
        AuthorizationServerContext ctx = new AuthorizationServerContext() {
            @Override
            public String getIssuer() { return "http://localhost"; }
            @Override
            public AuthorizationServerSettings getAuthorizationServerSettings() {
                return AuthorizationServerSettings.builder().issuer("http://localhost").build();
            }
        };
        AuthorizationServerContextHolder.setContext(ctx);
    }

    @AfterEach
    void tearDown() {
        // TASK-BE-274: defensive — ensure no test leaves the SAS_ROTATION_SKIP_KEY
        // bound on the static TransactionSynchronizationManager.
        if (TransactionSynchronizationManager.hasResource(
                DomainSyncOAuth2AuthorizationService.SAS_ROTATION_SKIP_KEY)) {
            TransactionSynchronizationManager.unbindResource(
                    DomainSyncOAuth2AuthorizationService.SAS_ROTATION_SKIP_KEY);
        }
        AuthorizationServerContextHolder.resetContext();
    }

    // -----------------------------------------------------------------------
    // supports()
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("supports: returns true for OAuth2RefreshTokenAuthenticationToken")
    void supports_refreshTokenToken_returnsTrue() {
        assertThat(provider.supports(OAuth2RefreshTokenAuthenticationToken.class)).isTrue();
    }

    @Test
    @DisplayName("supports: returns false for other authentication types")
    void supports_otherType_returnsFalse() {
        assertThat(provider.supports(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.class))
                .isFalse();
    }

    // -----------------------------------------------------------------------
    // Unauthenticated client → INVALID_CLIENT
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("authenticate: unauthenticated client principal → INVALID_CLIENT")
    void authenticate_unauthenticatedClient_throwsInvalidClient() {
        OAuth2RefreshTokenAuthenticationToken auth = mock(OAuth2RefreshTokenAuthenticationToken.class);
        when(auth.getPrincipal()).thenReturn("not-authenticated");

        assertThatThrownBy(() -> provider.authenticate(auth))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(OAuth2ErrorCodes.INVALID_CLIENT);
    }

    // -----------------------------------------------------------------------
    // Client does not support refresh_token grant → UNAUTHORIZED_CLIENT
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("authenticate: client not supporting refresh_token → UNAUTHORIZED_CLIENT")
    void authenticate_clientNotSupportingRefreshGrant_throwsUnauthorizedClient() {
        RegisteredClient registeredClient = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("client-no-refresh")
                .clientSecret("{noop}secret")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS) // no REFRESH_TOKEN
                .clientName("tenant|B2C")
                .build();

        OAuth2ClientAuthenticationToken clientPrincipal =
                new OAuth2ClientAuthenticationToken(registeredClient,
                        org.springframework.security.oauth2.core.ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
                        "secret");

        OAuth2RefreshTokenAuthenticationToken auth = mock(OAuth2RefreshTokenAuthenticationToken.class);
        when(auth.getPrincipal()).thenReturn(clientPrincipal);

        assertThatThrownBy(() -> provider.authenticate(auth))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(OAuth2ErrorCodes.UNAUTHORIZED_CLIENT);
    }

    // -----------------------------------------------------------------------
    // Token not found in SAS authorization service → INVALID_GRANT
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("authenticate: token not found in authorization service → INVALID_GRANT")
    void authenticate_tokenNotFound_throwsInvalidGrant() {
        RegisteredClient registeredClient = buildDemoSpaClient();
        OAuth2ClientAuthenticationToken clientPrincipal = buildAuthenticatedClient(registeredClient);

        OAuth2RefreshTokenAuthenticationToken auth = mock(OAuth2RefreshTokenAuthenticationToken.class);
        when(auth.getPrincipal()).thenReturn(clientPrincipal);
        when(auth.getRefreshToken()).thenReturn("unknown-token-value");

        when(authorizationService.findByToken("unknown-token-value", OAuth2TokenType.REFRESH_TOKEN))
                .thenReturn(null);

        assertThatThrownBy(() -> provider.authenticate(auth))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(OAuth2ErrorCodes.INVALID_GRANT);
    }

    // -----------------------------------------------------------------------
    // Reuse detection → INVALID_GRANT + revokeAll invoked
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("authenticate: reuse detected → INVALID_GRANT + revokeAllByAccountId called")
    void authenticate_reuseDetected_throwsInvalidGrantAndRevokesTokens() {
        RegisteredClient registeredClient = buildDemoSpaClient();
        OAuth2ClientAuthenticationToken clientPrincipal = buildAuthenticatedClient(registeredClient);

        String tokenValue = "rotated-rt-" + UUID.randomUUID();

        // Domain store token exists
        RefreshToken domainToken = RefreshToken.create(
                tokenValue, "account-001", "fan-platform",
                Instant.now().minusSeconds(60),
                Instant.now().plusSeconds(3600),
                null, null, null);

        // SAS authorization exists with an active refresh token
        OAuth2RefreshToken sasRt = new OAuth2RefreshToken(
                tokenValue, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        OAuth2Authorization authorization = buildAuthorization(registeredClient, "account-001", sasRt);

        OAuth2RefreshTokenAuthenticationToken auth = mock(OAuth2RefreshTokenAuthenticationToken.class);
        when(auth.getPrincipal()).thenReturn(clientPrincipal);
        when(auth.getRefreshToken()).thenReturn(tokenValue);

        when(authorizationService.findByToken(tokenValue, OAuth2TokenType.REFRESH_TOKEN))
                .thenReturn(authorization);
        when(refreshTokenRepository.findByJti(tokenValue)).thenReturn(Optional.of(domainToken));
        when(tokenReuseDetector.isReuse(domainToken)).thenReturn(true); // reuse detected

        // TASK-BE-606: the pre-check (before findByToken) sees no child yet; the in-flow
        // re-check sees the child a concurrent refresh committed — 60 s ago, so outside the
        // grace window → reuse.
        when(refreshTokenRepository.findAllByRotatedFrom(tokenValue))
                .thenReturn(List.of(), List.of(child(tokenValue, "account-001", NOW.minusSeconds(60))));
        when(deviceSessionRepository.findActiveByAccountId("account-001")).thenReturn(java.util.List.of());
        when(refreshTokenRepository.revokeAllByAccountId("account-001")).thenReturn(1);
        doNothing().when(bulkInvalidationStore).invalidateAll(eq("account-001"), anyLong());
        doNothing().when(authEventPublisher).publishTokenReuseDetected(
                any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyInt());

        assertThatThrownBy(() -> provider.authenticate(auth))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(OAuth2ErrorCodes.INVALID_GRANT);

        verify(refreshTokenRepository).revokeAllByAccountId("account-001");
        verify(bulkInvalidationStore).invalidateAll(eq("account-001"), anyLong());
        // TASK-BE-259: tenantId is now a required arg, sourced from the reused token's DB row.
        // TASK-BE-608: reusedJti is now a SHA-256 digest of the raw token, never the raw value.
        verify(authEventPublisher).publishTokenReuseDetected(
                eq("account-001"), eq("fan-platform"), eq(SasRefreshTokenAuthenticationProvider.reuseTokenDigest(tokenValue)),
                any(), any(), any(), any(), eq(true), eq(1));
    }

    // -----------------------------------------------------------------------
    // TASK-BE-465: identity preservation on rotation — the rotated access token
    // MUST be generated from the ORIGINAL resource-owner Authentication (stored by
    // SAS under `java.security.Principal`), NOT the client principal. A key mismatch
    // regression made every refresh fall back to the client principal, so the rotated
    // token carried `sub` = client_id (breaking `X-User-Id ← sub` UUID binding
    // downstream) and lost the account's `details` (account_id / tenant / roles).
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("authenticate: rotation reuses the stored resource-owner principal "
            + "(account_id preserved) — regression for sub=client_id on refresh")
    void authenticate_happyPath_generatesTokenFromStoredResourceOwnerPrincipal() {
        RegisteredClient registeredClient = buildDemoSpaClient();
        OAuth2ClientAuthenticationToken clientPrincipal = buildAuthenticatedClient(registeredClient);

        String tokenValue = "rotated-rt-" + UUID.randomUUID();
        String accountId = "01928c4a-7e9f-7c00-9a40-d2b1f5e8c500";

        // The resource-owner Authentication SAS persisted at authorization_code time,
        // carrying the account identity on its `details` map (exactly what
        // CredentialAuthenticationProvider / SocialLoginBrowserController set).
        Map<String, Object> details = new HashMap<>();
        details.put("tenant_id", "ecommerce");
        details.put("tenant_type", "B2C_CONSUMER");
        details.put("account_id", accountId);
        UsernamePasswordAuthenticationToken resourceOwner =
                new UsernamePasswordAuthenticationToken(
                        "shopper@example.com", null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
        resourceOwner.setDetails(details);

        OAuth2RefreshToken sasRt = new OAuth2RefreshToken(
                tokenValue, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(registeredClient)
                .id(UUID.randomUUID().toString())
                .principalName("shopper@example.com")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of("openid"))
                .token(sasRt)
                // SAS stores the resource-owner principal under this exact key.
                .attribute(java.security.Principal.class.getName(), resourceOwner)
                .build();

        OAuth2RefreshTokenAuthenticationToken auth = mock(OAuth2RefreshTokenAuthenticationToken.class);
        when(auth.getPrincipal()).thenReturn(clientPrincipal);
        when(auth.getRefreshToken()).thenReturn(tokenValue);
        when(authorizationService.findByToken(tokenValue, OAuth2TokenType.REFRESH_TOKEN))
                .thenReturn(authorization);
        // Not in the domain store → the reuse/expiry block is skipped (just-issued race path).
        when(refreshTokenRepository.findByJti(tokenValue)).thenReturn(Optional.empty());

        Instant now = Instant.now();
        OAuth2Token generatedAccess = mock(OAuth2Token.class);
        when(generatedAccess.getTokenValue()).thenReturn("new-access-jwt");
        when(generatedAccess.getIssuedAt()).thenReturn(now);
        when(generatedAccess.getExpiresAt()).thenReturn(now.plusSeconds(300));
        OAuth2Token generatedRefresh = mock(OAuth2Token.class);
        when(generatedRefresh.getTokenValue()).thenReturn("new-refresh-opaque");
        when(generatedRefresh.getIssuedAt()).thenReturn(now);
        when(generatedRefresh.getExpiresAt()).thenReturn(now.plusSeconds(3600));
        // access token first, refresh token second, ID token third (TASK-MONO-705 ⓐ —
        // the authorization carries `openid`, so an ID token is now generated too).
        doReturn(generatedAccess, generatedRefresh, buildIdTokenJwt("new-id-jwt", now))
                .when(tokenGenerator).generate(any());

        // The rotation write path registers a TransactionSynchronization inside the
        // TransactionTemplate callback; with a mocked PlatformTransactionManager no real
        // synchronization is initialized, so activate one for the duration of the call
        // (a real AbstractPlatformTransactionManager does this in getTransaction()).
        Authentication result;
        TransactionSynchronizationManager.initSynchronization();
        try {
            result = provider.authenticate(auth);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        assertThat(result).isInstanceOf(OAuth2AccessTokenAuthenticationToken.class);

        // The ACCESS token must be generated from a context whose principal is the
        // stored resource owner (carrying account_id) — NOT the client principal.
        ArgumentCaptor<OAuth2TokenContext> ctxCaptor = ArgumentCaptor.forClass(OAuth2TokenContext.class);
        verify(tokenGenerator, times(3)).generate(ctxCaptor.capture());
        OAuth2TokenContext accessCtx = ctxCaptor.getAllValues().get(0);
        Authentication ctxPrincipal = accessCtx.getPrincipal();

        assertThat(ctxPrincipal)
                .as("rotation must reuse the stored resource-owner principal, not the client")
                .isSameAs(resourceOwner);
        assertThat(ctxPrincipal).isNotSameAs(clientPrincipal);
        @SuppressWarnings("unchecked")
        Map<String, Object> ctxDetails = (Map<String, Object>) ctxPrincipal.getDetails();
        assertThat(ctxDetails)
                .as("the account identity that drives sub=account_id + roles must survive rotation")
                .containsEntry("account_id", accountId);
    }

    // -----------------------------------------------------------------------
    // TASK-BE-603 — the rotated mirror row, the refreshed event, and the reuse
    // kill-switch are keyed on the account UUID from the principal details, not
    // on the principal name (the login email, here 54 characters — longer than
    // refresh_tokens.account_id VARCHAR(36)).
    // -----------------------------------------------------------------------

    private static final String LONG_EMAIL = "first.last.long-name+tag@subdomain.example-company.com";

    @Test
    @DisplayName("rotation (TASK-BE-603): 54자 이메일 principal → 새 미러 행 · auth.token.refreshed 의 accountId = UUID")
    void rotation_longEmailPrincipal_mirrorRowAndEventCarryAccountUuid() {
        assertThat(LONG_EMAIL).hasSizeGreaterThan(36);
        String accountId = UUID.randomUUID().toString();
        RegisteredClient registeredClient = buildDemoSpaClient();
        OAuth2ClientAuthenticationToken clientPrincipal = buildAuthenticatedClient(registeredClient);

        String tokenValue = "rotated-rt-" + UUID.randomUUID();
        OAuth2RefreshToken sasRt = new OAuth2RefreshToken(
                tokenValue, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        OAuth2Authorization authorization = buildLoginAuthorization(
                registeredClient, LONG_EMAIL, accountId, Set.of("profile"), sasRt);

        OAuth2RefreshTokenAuthenticationToken auth = mock(OAuth2RefreshTokenAuthenticationToken.class);
        when(auth.getPrincipal()).thenReturn(clientPrincipal);
        when(auth.getRefreshToken()).thenReturn(tokenValue);
        when(authorizationService.findByToken(tokenValue, OAuth2TokenType.REFRESH_TOKEN))
                .thenReturn(authorization);
        RefreshToken existing = RefreshToken.create(
                tokenValue, accountId, "fan-platform",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), null, null, null);
        when(refreshTokenRepository.findByJti(tokenValue)).thenReturn(Optional.of(existing));
        when(tokenReuseDetector.isReuse(existing)).thenReturn(false);

        Instant now = Instant.now();
        OAuth2Token generatedAccess = mock(OAuth2Token.class);
        when(generatedAccess.getTokenValue()).thenReturn("new-access-jwt");
        when(generatedAccess.getIssuedAt()).thenReturn(now);
        when(generatedAccess.getExpiresAt()).thenReturn(now.plusSeconds(300));
        OAuth2Token generatedRefresh = mock(OAuth2Token.class);
        when(generatedRefresh.getTokenValue()).thenReturn("new-refresh-opaque");
        when(generatedRefresh.getIssuedAt()).thenReturn(now);
        when(generatedRefresh.getExpiresAt()).thenReturn(now.plusSeconds(3600));
        doReturn(generatedAccess, generatedRefresh).when(tokenGenerator).generate(any());

        TransactionSynchronizationManager.initSynchronization();
        try {
            provider.authenticate(auth);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, times(2)).save(saved.capture());
        RefreshToken newRow = saved.getAllValues().stream()
                .filter(t -> "new-refresh-opaque".equals(t.getJti()))
                .findFirst().orElseThrow();
        assertThat(newRow.getAccountId())
                .as("the rotated mirror row is keyed on the account UUID, never the login email")
                .isEqualTo(accountId);
        assertThat(newRow.getRotatedFrom()).isEqualTo(tokenValue);

        verify(authEventPublisher).publishTokenRefreshed(
                eq(accountId), eq("fan-platform"), eq(tokenValue), eq("new-refresh-opaque"), any());
    }

    @Test
    @DisplayName("reuse (TASK-BE-603): revoke-all · 기기 세션 · invalidate-all · 이벤트 = UUID, "
            + "배수 기간의 이메일 키 미러 행도 폐기")
    void reuse_longEmailPrincipal_keysOnUuidAndAlsoRevokesLegacyEmailRows() {
        String accountId = UUID.randomUUID().toString();
        RegisteredClient registeredClient = buildDemoSpaClient();
        OAuth2ClientAuthenticationToken clientPrincipal = buildAuthenticatedClient(registeredClient);

        String tokenValue = "rotated-rt-" + UUID.randomUUID();
        RefreshToken domainToken = RefreshToken.create(
                tokenValue, accountId, "fan-platform",
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), null, null, null);
        OAuth2RefreshToken sasRt = new OAuth2RefreshToken(
                tokenValue, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        OAuth2Authorization authorization = buildLoginAuthorization(
                registeredClient, LONG_EMAIL, accountId, Set.of("openid"), sasRt);

        OAuth2RefreshTokenAuthenticationToken auth = mock(OAuth2RefreshTokenAuthenticationToken.class);
        when(auth.getPrincipal()).thenReturn(clientPrincipal);
        when(auth.getRefreshToken()).thenReturn(tokenValue);
        when(authorizationService.findByToken(tokenValue, OAuth2TokenType.REFRESH_TOKEN))
                .thenReturn(authorization);
        when(refreshTokenRepository.findByJti(tokenValue)).thenReturn(Optional.of(domainToken));
        when(tokenReuseDetector.isReuse(domainToken)).thenReturn(true);
        when(refreshTokenRepository.findAllByRotatedFrom(tokenValue))
                .thenReturn(List.of(), List.of(child(tokenValue, accountId, NOW.minusSeconds(60))));
        when(deviceSessionRepository.findActiveByAccountId(accountId)).thenReturn(List.of());
        when(refreshTokenRepository.revokeAllByAccountId(accountId)).thenReturn(2);
        // A session issued before TASK-BE-603 whose mirror row still carries the email.
        when(refreshTokenRepository.revokeAllByAccountId(LONG_EMAIL)).thenReturn(1);

        assertThatThrownBy(() -> provider.authenticate(auth))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(OAuth2ErrorCodes.INVALID_GRANT);

        verify(refreshTokenRepository).revokeAllByAccountId(accountId);
        verify(refreshTokenRepository).revokeAllByAccountId(LONG_EMAIL);
        verify(deviceSessionRepository).findActiveByAccountId(accountId);
        verify(bulkInvalidationStore).invalidateAll(eq(accountId), anyLong());
        verify(authEventPublisher).publishTokenReuseDetected(
                eq(accountId), eq("fan-platform"), eq(SasRefreshTokenAuthenticationProvider.reuseTokenDigest(tokenValue)),
                any(), any(), any(), any(), eq(true), eq(3));
    }

    // -----------------------------------------------------------------------
    // TASK-BE-604 — the tenant check compares the mirror row with the SESSION's
    // login-time tenant (principal details tenant_id = the token's tenant_id claim),
    // not with the client's tenant; the rotated row carries that same tenant.
    //
    // 🔵 Cell A is the population the old client-tenant comparison broke: a console
    //    (tenant iam) session of an account whose credential lives in fan-platform.
    //    It refreshed only because SAS's built-in provider retried the grant.
    // 🔴 Cell B is the control that keeps the check a check: a row whose tenant is
    //    NOT the session's is still refused.
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("BE-604 A: cross-tenant console session (client iam, login tenant fan-platform, row fan-platform) "
            + "→ rotates; new row + refreshed event carry fan-platform; no mismatch event")
    void crossTenantConsoleSession_rowMatchesSessionTenant_rotates() {
        String accountId = UUID.randomUUID().toString();
        RegisteredClient consoleClient = buildClientInTenant("platform-console-web", "iam");
        String tokenValue = "rt-" + UUID.randomUUID();
        OAuth2Authorization authorization = buildLoginAuthorization(consoleClient,
                "operator@example.com", accountId, Set.of("profile"), activeRefreshToken(tokenValue));
        RefreshToken row = mirrorRow(tokenValue, accountId, "fan-platform");

        rotateSuccessfully(consoleClient, authorization, tokenValue, Optional.of(row));

        RefreshToken rotated = savedRow("new-refresh-opaque");
        assertThat(rotated.getTenantId())
                .as("the rotated row carries the login-time tenant, like the first row — not the client's iam")
                .isEqualTo("fan-platform");
        verify(authEventPublisher).publishTokenRefreshed(
                eq(accountId), eq("fan-platform"), eq(tokenValue), eq("new-refresh-opaque"), any());
        verify(authEventPublisher, never()).publishTokenTenantMismatch(
                any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("BE-604 B (control): row tenant ≠ session tenant → INVALID_GRANT TOKEN_TENANT_MISMATCH, "
            + "event expected = session tenant, nothing minted")
    void rowTenantDiffersFromSessionTenant_rejected() {
        String accountId = UUID.randomUUID().toString();
        RegisteredClient consoleClient = buildClientInTenant("platform-console-web", "iam");
        OAuth2ClientAuthenticationToken clientPrincipal = buildAuthenticatedClient(consoleClient);
        String tokenValue = "rt-" + UUID.randomUUID();
        OAuth2Authorization authorization = buildLoginAuthorization(consoleClient,
                "operator@example.com", accountId, Set.of("profile"), activeRefreshToken(tokenValue));
        // The row carries the CLIENT's tenant — exactly what the pre-BE-604 persistRotation wrote.
        RefreshToken row = mirrorRow(tokenValue, accountId, "iam");

        OAuth2RefreshTokenAuthenticationToken auth = refreshRequest(clientPrincipal, tokenValue);
        when(authorizationService.findByToken(tokenValue, OAuth2TokenType.REFRESH_TOKEN))
                .thenReturn(authorization);
        when(refreshTokenRepository.findByJti(tokenValue)).thenReturn(Optional.of(row));
        when(tokenReuseDetector.isReuse(row)).thenReturn(false);

        assertThatThrownBy(() -> provider.authenticate(auth))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> {
                    var error = ((OAuth2AuthenticationException) e).getError();
                    assertThat(error.getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_GRANT);
                    assertThat(error.getDescription()).isEqualTo("TOKEN_TENANT_MISMATCH");
                });

        verify(authEventPublisher).publishTokenTenantMismatch(
                eq(accountId), eq("iam"), eq("fan-platform"), eq(tokenValue), any(), any());
        verifyNoInteractions(tokenGenerator);
        verify(authorizationService, never()).save(any());
    }

    @Test
    @DisplayName("BE-604: a principal WITHOUT tenant details → session tenant = client tenant "
            + "(the claim's own fallback) — a row of another tenant is refused")
    void principalWithoutTenantDetails_fallsBackToClientTenant() {
        RegisteredClient registeredClient = buildDemoSpaClient(); // fan-platform
        OAuth2ClientAuthenticationToken clientPrincipal = buildAuthenticatedClient(registeredClient);
        String tokenValue = "rt-" + UUID.randomUUID();
        OAuth2Authorization authorization =
                buildAuthorization(registeredClient, "no-details-principal", activeRefreshToken(tokenValue));
        RefreshToken row = mirrorRow(tokenValue, "no-details-principal", "ecommerce");

        OAuth2RefreshTokenAuthenticationToken auth = refreshRequest(clientPrincipal, tokenValue);
        when(authorizationService.findByToken(tokenValue, OAuth2TokenType.REFRESH_TOKEN))
                .thenReturn(authorization);
        when(refreshTokenRepository.findByJti(tokenValue)).thenReturn(Optional.of(row));
        when(tokenReuseDetector.isReuse(row)).thenReturn(false);

        assertThatThrownBy(() -> provider.authenticate(auth))
                .isInstanceOf(OAuth2AuthenticationException.class);

        verify(authEventPublisher).publishTokenTenantMismatch(
                eq("no-details-principal"), eq("ecommerce"), eq("fan-platform"), eq(tokenValue), any(), any());
    }

    @Test
    @DisplayName("BE-604 AC-1 (unit leg): revoked mirror row → INVALID_GRANT and nothing is minted or saved")
    void revokedMirrorRow_rejected() {
        String accountId = UUID.randomUUID().toString();
        RegisteredClient registeredClient = buildDemoSpaClient();
        OAuth2ClientAuthenticationToken clientPrincipal = buildAuthenticatedClient(registeredClient);
        String tokenValue = "rt-" + UUID.randomUUID();
        OAuth2Authorization authorization = buildLoginAuthorization(registeredClient,
                "user@example.com", accountId, Set.of("openid"), activeRefreshToken(tokenValue));
        RefreshToken row = mirrorRow(tokenValue, accountId, "fan-platform");
        row.revoke();

        OAuth2RefreshTokenAuthenticationToken auth = refreshRequest(clientPrincipal, tokenValue);
        when(authorizationService.findByToken(tokenValue, OAuth2TokenType.REFRESH_TOKEN))
                .thenReturn(authorization);
        when(refreshTokenRepository.findByJti(tokenValue)).thenReturn(Optional.of(row));
        when(tokenReuseDetector.isReuse(row)).thenReturn(false);

        assertThatThrownBy(() -> provider.authenticate(auth))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(OAuth2ErrorCodes.INVALID_GRANT);

        verifyNoInteractions(tokenGenerator);
        verify(authorizationService, never()).save(any());
        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("BE-604 side defect: mirror row MISSING on a cross-tenant session → the row this rotation "
            + "writes carries the session tenant, so the session's own next refresh accepts it")
    void missingMirrorRow_rotatedRowCarriesSessionTenant() {
        String accountId = UUID.randomUUID().toString();
        RegisteredClient consoleClient = buildClientInTenant("platform-console-web", "iam");
        String tokenValue = "rt-" + UUID.randomUUID();
        OAuth2Authorization authorization = buildLoginAuthorization(consoleClient,
                "operator@example.com", accountId, Set.of("profile"), activeRefreshToken(tokenValue));

        rotateSuccessfully(consoleClient, authorization, tokenValue, Optional.empty());

        assertThat(savedRow("new-refresh-opaque").getTenantId()).isEqualTo("fan-platform");
    }

    // -----------------------------------------------------------------------
    // TASK-BE-606 — a token that was rotated away is judged from the mirror store's
    // rotation chain BEFORE findByToken (SAS no longer knows it), with a 30 s grace
    // window for client races (owner decision 2026-09-26, option B).
    //
    // 🔴 Each reuse cell asserts findByToken(A) is never asked: the defect was that the
    //    SAS lookup answered first. 🔵 The grace cells are the control group — without
    //    them "always revoke on any child" passes every reuse cell.
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("BE-606 AC-1 (unit): 회전된 토큰 A 를 유예 밖(60s)에 재제출 → findByToken 전에 재사용 판정 · "
            + "미러 행 + SAS 인가 폐기 · reuse 이벤트 1건(originalRotationAt = 자식 발급 시각)")
    void rotatedAwayToken_beyondGrace_revokesFamilyBeforeSasLookup() {
        String accountId = UUID.randomUUID().toString();
        RegisteredClient client = buildDemoSpaClient();
        String tokenA = "rt-A-" + UUID.randomUUID();
        String tokenB = "rt-B-" + UUID.randomUUID();
        RefreshToken childB = RefreshToken.create(tokenB, accountId, "fan-platform",
                NOW.minusSeconds(60), NOW.plusSeconds(3600), tokenA, null, null);
        OAuth2Authorization holdsB = buildLoginAuthorization(client, "user@example.com", accountId,
                Set.of("openid"), activeRefreshToken(tokenB));

        OAuth2RefreshTokenAuthenticationToken auth = refreshRequest(buildAuthenticatedClient(client), tokenA);
        when(refreshTokenRepository.findAllByRotatedFrom(tokenA)).thenReturn(List.of(childB));
        // The family walk: B is the head (nothing rotated from it) → its authorization.
        when(refreshTokenRepository.findAllByRotatedFrom(tokenB)).thenReturn(List.of());
        when(authorizationService.findByToken(tokenB, OAuth2TokenType.REFRESH_TOKEN)).thenReturn(holdsB);
        when(deviceSessionRepository.findActiveByAccountId(accountId)).thenReturn(List.of());
        when(refreshTokenRepository.revokeAllByAccountId(accountId)).thenReturn(1);
        when(authorizationRevocationPort.revokeActiveRefreshTokens(accountId)).thenReturn(1);

        assertThatThrownBy(() -> provider.authenticate(auth))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> {
                    var error = ((OAuth2AuthenticationException) e).getError();
                    assertThat(error.getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_GRANT);
                    assertThat(error.getDescription()).contains("reuse detected");
                });

        verify(authorizationService, never()).findByToken(tokenA, OAuth2TokenType.REFRESH_TOKEN);
        verify(refreshTokenRepository).revokeAllByAccountId(accountId);
        // Drain window: the principal name (email) is revoked as a legacy mirror key too.
        verify(refreshTokenRepository).revokeAllByAccountId("user@example.com");
        verify(authorizationRevocationPort).revokeActiveRefreshTokens(accountId);
        verify(bulkInvalidationStore).invalidateAll(eq(accountId), anyLong());
        verify(authEventPublisher, times(1)).publishTokenReuseDetected(
                eq(accountId), eq("fan-platform"), eq(SasRefreshTokenAuthenticationProvider.reuseTokenDigest(tokenA)),
                eq(childB.getIssuedAt()), eq(NOW),
                any(), any(), eq(true), eq(2));
        verifyNoInteractions(tokenGenerator);
        verify(authorizationService, never()).save(any());
    }

    @Test
    @DisplayName("BE-606 AC-2 (unit): 회전 10s 뒤 재제출(자식=체인 머리) → 400 invalid_grant, 폐기·이벤트 없음")
    void rotatedAwayToken_withinGrace_refusedWithoutRevoking() {
        RegisteredClient client = buildDemoSpaClient();
        String tokenA = "rt-A-" + UUID.randomUUID();
        RefreshToken childB = child(tokenA, UUID.randomUUID().toString(), NOW.minusSeconds(10));

        OAuth2RefreshTokenAuthenticationToken auth = refreshRequest(buildAuthenticatedClient(client), tokenA);
        when(refreshTokenRepository.findAllByRotatedFrom(tokenA)).thenReturn(List.of(childB));
        when(refreshTokenRepository.existsByRotatedFrom(childB.getJti())).thenReturn(false);

        assertThatThrownBy(() -> provider.authenticate(auth))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .satisfies(e -> {
                    var error = ((OAuth2AuthenticationException) e).getError();
                    assertThat(error.getErrorCode()).isEqualTo(OAuth2ErrorCodes.INVALID_GRANT);
                    assertThat(error.getDescription()).isNull();
                });

        verify(refreshTokenRepository, never()).revokeAllByAccountId(any());
        verifyNoInteractions(authorizationRevocationPort, authEventPublisher, bulkInvalidationStore,
                deviceSessionRepository, tokenGenerator, authorizationService);
    }

    @Test
    @DisplayName("BE-606: 유예 안(10s)이라도 자식이 이미 다시 회전됨(손자 존재) → 재사용")
    void rotatedAwayToken_withinWindowButChildAlreadyRotated_isReuse() {
        String accountId = UUID.randomUUID().toString();
        RegisteredClient client = buildDemoSpaClient();
        String tokenA = "rt-A-" + UUID.randomUUID();
        RefreshToken childB = child(tokenA, accountId, NOW.minusSeconds(10));

        OAuth2RefreshTokenAuthenticationToken auth = refreshRequest(buildAuthenticatedClient(client), tokenA);
        when(refreshTokenRepository.findAllByRotatedFrom(tokenA)).thenReturn(List.of(childB));
        when(refreshTokenRepository.existsByRotatedFrom(childB.getJti())).thenReturn(true);
        when(refreshTokenRepository.revokeAllByAccountId(accountId)).thenReturn(1);

        assertThatThrownBy(() -> provider.authenticate(auth)).isInstanceOf(OAuth2AuthenticationException.class);

        verify(authEventPublisher).publishTokenReuseDetected(
                eq(accountId), any(), eq(SasRefreshTokenAuthenticationProvider.reuseTokenDigest(tokenA)),
                any(), any(), any(), any(), eq(true), eq(1));
    }

    @Test
    @DisplayName("BE-606 AC-2 (unit): 자식 둘(동시 refresh 가 둘 다 통과한 상태) · 유예 안 → 예외 없이 재사용 판정")
    void rotatedAwayToken_twoChildren_isReuseWithoutThrowing() {
        String accountId = UUID.randomUUID().toString();
        RegisteredClient client = buildDemoSpaClient();
        String tokenA = "rt-A-" + UUID.randomUUID();
        RefreshToken childB1 = child(tokenA, accountId, NOW.minusSeconds(3));
        RefreshToken childB2 = child(tokenA, accountId, NOW.minusSeconds(2));

        OAuth2RefreshTokenAuthenticationToken auth = refreshRequest(buildAuthenticatedClient(client), tokenA);
        when(refreshTokenRepository.findAllByRotatedFrom(tokenA)).thenReturn(List.of(childB2, childB1));
        when(refreshTokenRepository.revokeAllByAccountId(accountId)).thenReturn(2);

        assertThatThrownBy(() -> provider.authenticate(auth))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getDescription())
                .asString().contains("reuse detected");

        // originalRotationAt = the EARLIEST child, whatever order the store returned them in.
        verify(authEventPublisher).publishTokenReuseDetected(
                eq(accountId), any(), eq(SasRefreshTokenAuthenticationProvider.reuseTokenDigest(tokenA)),
                eq(childB1.getIssuedAt()), any(), any(), any(),
                eq(true), eq(2));
    }

    @Test
    @DisplayName("BE-606: 이미 닫힌 패밀리(폐기할 것 0) 재제출 → 400, 이벤트 없음(중복 발행·잠금 누적 방지)")
    void rotatedAwayToken_familyAlreadyClosed_noEvent() {
        String accountId = UUID.randomUUID().toString();
        RegisteredClient client = buildDemoSpaClient();
        String tokenA = "rt-A-" + UUID.randomUUID();

        OAuth2RefreshTokenAuthenticationToken auth = refreshRequest(buildAuthenticatedClient(client), tokenA);
        when(refreshTokenRepository.findAllByRotatedFrom(tokenA))
                .thenReturn(List.of(child(tokenA, accountId, NOW.minusSeconds(600))));

        assertThatThrownBy(() -> provider.authenticate(auth)).isInstanceOf(OAuth2AuthenticationException.class);

        verify(refreshTokenRepository).revokeAllByAccountId(accountId);
        verify(authorizationRevocationPort).revokeActiveRefreshTokens(accountId);
        verify(authEventPublisher, never()).publishTokenReuseDetected(
                any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyInt());
    }

    @Test
    @DisplayName("BE-606: 동시 refresh 경쟁 — 사전 검사 뒤 자식이 커밋됨(in-flow) · 5s 전 → 유예, 폐기 없음")
    void inFlightRace_withinGrace_refusedWithoutRevoking() {
        String accountId = UUID.randomUUID().toString();
        RegisteredClient client = buildDemoSpaClient();
        OAuth2ClientAuthenticationToken clientPrincipal = buildAuthenticatedClient(client);
        String tokenA = "rt-A-" + UUID.randomUUID();
        OAuth2Authorization authorization = buildLoginAuthorization(client, "user@example.com", accountId,
                Set.of("openid"), activeRefreshToken(tokenA));
        RefreshToken rowA = mirrorRow(tokenA, accountId, "fan-platform");
        RefreshToken childB = child(tokenA, accountId, NOW.minusSeconds(5));

        OAuth2RefreshTokenAuthenticationToken auth = refreshRequest(clientPrincipal, tokenA);
        when(refreshTokenRepository.findAllByRotatedFrom(tokenA)).thenReturn(List.of(), List.of(childB));
        when(authorizationService.findByToken(tokenA, OAuth2TokenType.REFRESH_TOKEN)).thenReturn(authorization);
        when(refreshTokenRepository.findByJti(tokenA)).thenReturn(Optional.of(rowA));
        when(tokenReuseDetector.isReuse(rowA)).thenReturn(true);
        when(refreshTokenRepository.existsByRotatedFrom(childB.getJti())).thenReturn(false);

        assertThatThrownBy(() -> provider.authenticate(auth))
                .isInstanceOf(OAuth2AuthenticationException.class)
                .extracting(e -> ((OAuth2AuthenticationException) e).getError().getErrorCode())
                .isEqualTo(OAuth2ErrorCodes.INVALID_GRANT);

        verify(refreshTokenRepository, never()).revokeAllByAccountId(any());
        verifyNoInteractions(authorizationRevocationPort, authEventPublisher, tokenGenerator);
    }

    @Test
    @DisplayName("BE-606: 체인 머리를 쥔 SAS 인가가 없음 → 가장 이른 자식 행의 account_id 로 폐기")
    void rotatedAwayToken_noLiveHead_fallsBackToChildAccountId() {
        String accountId = UUID.randomUUID().toString();
        RegisteredClient client = buildDemoSpaClient();
        String tokenA = "rt-A-" + UUID.randomUUID();
        RefreshToken childB = child(tokenA, accountId, NOW.minusSeconds(120));

        OAuth2RefreshTokenAuthenticationToken auth = refreshRequest(buildAuthenticatedClient(client), tokenA);
        when(refreshTokenRepository.findAllByRotatedFrom(tokenA)).thenReturn(List.of(childB));
        when(refreshTokenRepository.revokeAllByAccountId(accountId)).thenReturn(1);

        assertThatThrownBy(() -> provider.authenticate(auth)).isInstanceOf(OAuth2AuthenticationException.class);

        verify(authorizationService).findByToken(childB.getJti(), OAuth2TokenType.REFRESH_TOKEN);
        verify(refreshTokenRepository).revokeAllByAccountId(accountId);
        verify(authorizationRevocationPort).revokeActiveRefreshTokens(accountId);
        verify(authEventPublisher).publishTokenReuseDetected(
                eq(accountId), eq("fan-platform"), eq(SasRefreshTokenAuthenticationProvider.reuseTokenDigest(tokenA)),
                any(), any(), any(), any(), eq(true), eq(1));
    }

    /** A mirror row rotated from {@code parent}, issued at {@code issuedAt} (tenant fan-platform). */
    private static RefreshToken child(String parent, String accountId, Instant issuedAt) {
        return RefreshToken.create("rt-child-" + UUID.randomUUID(), accountId, "fan-platform",
                issuedAt, issuedAt.plusSeconds(3600), parent, null, null);
    }

    private static OAuth2RefreshToken activeRefreshToken(String value) {
        return new OAuth2RefreshToken(value, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
    }

    private static RefreshToken mirrorRow(String jti, String accountId, String tenantId) {
        return RefreshToken.create(jti, accountId, tenantId,
                Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600), null, null, null);
    }

    private static OAuth2RefreshTokenAuthenticationToken refreshRequest(
            OAuth2ClientAuthenticationToken clientPrincipal, String tokenValue) {
        OAuth2RefreshTokenAuthenticationToken auth = mock(OAuth2RefreshTokenAuthenticationToken.class);
        when(auth.getPrincipal()).thenReturn(clientPrincipal);
        when(auth.getRefreshToken()).thenReturn(tokenValue);
        return auth;
    }

    /** Drives one rotation that must succeed; stubs access + refresh generation only (no openid). */
    private void rotateSuccessfully(RegisteredClient client, OAuth2Authorization authorization,
                                    String tokenValue, Optional<RefreshToken> existingRow) {
        OAuth2RefreshTokenAuthenticationToken auth =
                refreshRequest(buildAuthenticatedClient(client), tokenValue);
        when(authorizationService.findByToken(tokenValue, OAuth2TokenType.REFRESH_TOKEN))
                .thenReturn(authorization);
        when(refreshTokenRepository.findByJti(tokenValue)).thenReturn(existingRow);
        existingRow.ifPresent(row -> when(tokenReuseDetector.isReuse(row)).thenReturn(false));

        Instant now = Instant.now();
        OAuth2Token generatedAccess = mock(OAuth2Token.class);
        when(generatedAccess.getTokenValue()).thenReturn("new-access-jwt");
        when(generatedAccess.getIssuedAt()).thenReturn(now);
        when(generatedAccess.getExpiresAt()).thenReturn(now.plusSeconds(300));
        OAuth2Token generatedRefresh = mock(OAuth2Token.class);
        when(generatedRefresh.getTokenValue()).thenReturn("new-refresh-opaque");
        when(generatedRefresh.getIssuedAt()).thenReturn(now);
        when(generatedRefresh.getExpiresAt()).thenReturn(now.plusSeconds(3600));
        doReturn(generatedAccess, generatedRefresh).when(tokenGenerator).generate(any());

        TransactionSynchronizationManager.initSynchronization();
        try {
            assertThat(provider.authenticate(auth)).isInstanceOf(OAuth2AccessTokenAuthenticationToken.class);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private RefreshToken savedRow(String jti) {
        ArgumentCaptor<RefreshToken> saved = ArgumentCaptor.forClass(RefreshToken.class);
        verify(refreshTokenRepository, atLeastOnce()).save(saved.capture());
        return saved.getAllValues().stream()
                .filter(t -> jti.equals(t.getJti()))
                .findFirst().orElseThrow();
    }

    private RegisteredClient buildClientInTenant(String clientId, String tenantId) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientAuthenticationMethod(
                        org.springframework.security.oauth2.core.ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:3000/api/auth/callback")
                .scope("openid")
                .clientName(tenantId + "|B2B")
                .build();
    }

    /** An authorization carrying the principal shape the browser login paths build. */
    private OAuth2Authorization buildLoginAuthorization(RegisteredClient client, String email,
                                                         String accountId, Set<String> scopes,
                                                         OAuth2RefreshToken refreshToken) {
        return OAuth2Authorization.withRegisteredClient(client)
                .id(UUID.randomUUID().toString())
                .principalName(email)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(scopes)
                .token(refreshToken)
                .attribute(java.security.Principal.class.getName(),
                        DomainSyncOAuth2AuthorizationServiceTest.loginPrincipal(email, accountId))
                .build();
    }

    // -----------------------------------------------------------------------
    // TASK-MONO-705 ⓐ — the rotated response must carry an ID token
    //
    // 🔴 Why these three cells and not one. The defect was not "no ID token is
    //    generated" — it was that the response and the stored authorization BOTH
    //    lacked one, and each of those breaks a different thing:
    //      · response missing   → the console never re-sets `console_id_token`
    //      · store missing      → OidcLogoutAuthenticationProvider cannot resolve
    //                             the authorization by `id_token_hint`
    //    A test that only asserted the first would go green on a change that hands
    //    out an ID token nobody can log out with.
    // 🔵 The third cell is the control group: no `openid`, no ID token. Without it
    //    "always attach an ID token" would pass the other two.
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("authenticate: openid authorization → response carries id_token "
            + "(TASK-MONO-705 ⓐ — the console re-sets console_id_token from this)")
    void authenticate_openidScope_returnsIdTokenInAdditionalParameters() {
        RotationFixture f = rotate(Set.of("openid"), "issued-id-jwt");

        assertThat(f.result.getAdditionalParameters())
                .as("the refresh response must hand back an id_token — auth-api.md "
                        + "§ POST /oauth2/token promises it whenever scope contains openid")
                .containsEntry(OidcParameterNames.ID_TOKEN, "issued-id-jwt");
    }

    @Test
    @DisplayName("authenticate: the new id_token is stored ON the authorization "
            + "— RP-initiated logout resolves it with findByToken(hint, ID_TOKEN)")
    void authenticate_openidScope_storesIdTokenOnAuthorization() {
        RotationFixture f = rotate(Set.of("openid"), "issued-id-jwt");

        ArgumentCaptor<OAuth2Authorization> saved = ArgumentCaptor.forClass(OAuth2Authorization.class);
        verify(authorizationService).save(saved.capture());

        // 🔴 `getToken(String)` looks up by token VALUE, not by type — the first draft
        //    of this assertion passed "id_token" to it and got null from a correct
        //    implementation. The type-keyed overload is the one that mirrors how the
        //    authorization actually stores it.
        OAuth2Authorization.Token<OidcIdToken> stored = saved.getValue().getToken(OidcIdToken.class);
        assertThat(stored)
                .as("an id_token handed out but not stored fails logout — which is the "
                        + "very thing TASK-MONO-705 exists to restore")
                .isNotNull();
        assertThat(stored.getToken().getTokenValue()).isEqualTo("issued-id-jwt");
    }

    @Test
    @DisplayName("authenticate: authorization WITHOUT openid → no id_token anywhere "
            + "(control group — a non-OIDC client must not start receiving one)")
    void authenticate_withoutOpenidScope_omitsIdToken() {
        RotationFixture f = rotate(Set.of("profile"), null);

        assertThat(f.result.getAdditionalParameters())
                .as("no openid scope ⇒ nothing to hand back")
                .doesNotContainKey(OidcParameterNames.ID_TOKEN);
        // access + refresh only — the ID-token context must never be built.
        verify(tokenGenerator, times(2)).generate(any());
    }

    /** Result + captured context of one rotation, so the three cells above stay short. */
    private record RotationFixture(OAuth2AccessTokenAuthenticationToken result) {}

    /**
     * Drives one successful rotation.
     *
     * @param scopes    authorized scopes stored on the authorization
     * @param idTokenValue value the generator returns for the ID token, or {@code null}
     *                     to stub only the access + refresh tokens
     */
    private RotationFixture rotate(Set<String> scopes, String idTokenValue) {
        RegisteredClient registeredClient = buildDemoSpaClient();
        OAuth2ClientAuthenticationToken clientPrincipal = buildAuthenticatedClient(registeredClient);

        String tokenValue = "rotated-rt-" + UUID.randomUUID();
        OAuth2RefreshToken sasRt = new OAuth2RefreshToken(
                tokenValue, Instant.now().minusSeconds(60), Instant.now().plusSeconds(3600));
        OAuth2Authorization authorization = OAuth2Authorization.withRegisteredClient(registeredClient)
                .id(UUID.randomUUID().toString())
                .principalName("shopper@example.com")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(scopes)
                .token(sasRt)
                .build();

        OAuth2RefreshTokenAuthenticationToken auth = mock(OAuth2RefreshTokenAuthenticationToken.class);
        when(auth.getPrincipal()).thenReturn(clientPrincipal);
        when(auth.getRefreshToken()).thenReturn(tokenValue);
        when(authorizationService.findByToken(tokenValue, OAuth2TokenType.REFRESH_TOKEN))
                .thenReturn(authorization);
        when(refreshTokenRepository.findByJti(tokenValue)).thenReturn(Optional.empty());

        Instant now = Instant.now();
        OAuth2Token generatedAccess = mock(OAuth2Token.class);
        when(generatedAccess.getTokenValue()).thenReturn("new-access-jwt");
        when(generatedAccess.getIssuedAt()).thenReturn(now);
        when(generatedAccess.getExpiresAt()).thenReturn(now.plusSeconds(300));
        OAuth2Token generatedRefresh = mock(OAuth2Token.class);
        when(generatedRefresh.getTokenValue()).thenReturn("new-refresh-opaque");
        when(generatedRefresh.getIssuedAt()).thenReturn(now);
        when(generatedRefresh.getExpiresAt()).thenReturn(now.plusSeconds(3600));

        if (idTokenValue != null) {
            doReturn(generatedAccess, generatedRefresh, buildIdTokenJwt(idTokenValue, now))
                    .when(tokenGenerator).generate(any());
        } else {
            doReturn(generatedAccess, generatedRefresh).when(tokenGenerator).generate(any());
        }

        Authentication result;
        TransactionSynchronizationManager.initSynchronization();
        try {
            result = provider.authenticate(auth);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
        return new RotationFixture((OAuth2AccessTokenAuthenticationToken) result);
    }

    /**
     * A real {@link Jwt} — not a mock. The provider requires the generated ID token to
     * be a {@code Jwt} (it reads the claim set to store alongside the token), so a bare
     * {@code OAuth2Token} mock would exercise the failure branch instead of this one.
     */
    private static Jwt buildIdTokenJwt(String tokenValue, Instant now) {
        return Jwt.withTokenValue(tokenValue)
                .header("alg", "RS256")
                .claim("sub", "01928c4a-7e9f-7c00-9a40-d2b1f5e8c500")
                .claim("aud", List.of("demo-spa"))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(1800))
                .build();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private RegisteredClient buildDemoSpaClient() {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("demo-spa-client")
                .clientAuthenticationMethod(
                        org.springframework.security.oauth2.core.ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:3000/callback")
                .scope("openid")
                .clientName("fan-platform|B2C")
                .build();
    }

    private OAuth2ClientAuthenticationToken buildAuthenticatedClient(RegisteredClient client) {
        return new OAuth2ClientAuthenticationToken(
                client,
                org.springframework.security.oauth2.core.ClientAuthenticationMethod.NONE,
                null);
    }

    private OAuth2Authorization buildAuthorization(RegisteredClient client, String principalName,
                                                    OAuth2RefreshToken refreshToken) {
        return OAuth2Authorization.withRegisteredClient(client)
                .id(UUID.randomUUID().toString())
                .principalName(principalName)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizedScopes(Set.of("openid"))
                .token(refreshToken)
                .build();
    }
}
