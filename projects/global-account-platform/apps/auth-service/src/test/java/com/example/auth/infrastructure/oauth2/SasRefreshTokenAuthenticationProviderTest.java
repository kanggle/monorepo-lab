package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.domain.repository.BulkInvalidationStore;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.domain.token.RefreshToken;
import com.example.auth.domain.token.TokenReuseDetector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2Authorization;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationService;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2RefreshTokenAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContext;
import org.springframework.security.oauth2.server.authorization.context.AuthorizationServerContextHolder;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.time.Instant;
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

    private SasRefreshTokenAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SasRefreshTokenAuthenticationProvider(
                authorizationService,
                tokenGenerator,
                refreshTokenRepository,
                tokenReuseDetector,
                bulkInvalidationStore,
                deviceSessionRepository,
                authEventPublisher);

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

        // For handleReuseDetected
        when(refreshTokenRepository.findByRotatedFrom(tokenValue)).thenReturn(Optional.empty());
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
        verify(authEventPublisher).publishTokenReuseDetected(
                eq("account-001"), eq("fan-platform"), eq(tokenValue),
                any(), any(), any(), any(), eq(true), eq(1));
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
