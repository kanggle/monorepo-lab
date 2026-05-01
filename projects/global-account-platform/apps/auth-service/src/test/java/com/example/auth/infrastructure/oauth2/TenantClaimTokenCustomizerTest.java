package com.example.auth.infrastructure.oauth2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link TenantClaimTokenCustomizer}.
 *
 * <p>Verifies that {@code tenant_id} and {@code tenant_type} claims are correctly
 * injected into access tokens for the {@code client_credentials} grant, and that
 * fail-closed behaviour is enforced when tenant metadata is absent or malformed.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class TenantClaimTokenCustomizerTest {

    private TenantClaimTokenCustomizer customizer;

    @Mock
    private JwtEncodingContext context;

    @BeforeEach
    void setUp() {
        customizer = new TenantClaimTokenCustomizer();
    }

    // -----------------------------------------------------------------------
    // client_credentials — happy path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("client_credentials: injects tenant_id and tenant_type from clientName")
    void clientCredentials_injectsTenantClaims() {
        RegisteredClient client = buildClient("fan-platform|B2C");
        JwtClaimsSet.Builder claimsBuilder = JwtClaimsSet.builder()
                .issuer("http://localhost:8081")
                .subject("test-internal-client")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1800));

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.CLIENT_CREDENTIALS);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("fan-platform");
        assertThat((String) built.getClaim("tenant_type")).isEqualTo("B2C");
    }

    @Test
    @DisplayName("client_credentials: tenant with spaces trimmed")
    void clientCredentials_trimsTenantValues() {
        RegisteredClient client = buildClient("  wms-tenant  |  B2B  ");
        JwtClaimsSet.Builder claimsBuilder = baseClaimsBuilder();

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.CLIENT_CREDENTIALS);
        when(context.getRegisteredClient()).thenReturn(client);
        when(context.getClaims()).thenReturn(claimsBuilder);

        customizer.customize(context);

        JwtClaimsSet built = claimsBuilder.build();
        assertThat((String) built.getClaim("tenant_id")).isEqualTo("wms-tenant");
        assertThat((String) built.getClaim("tenant_type")).isEqualTo("B2B");
    }

    // -----------------------------------------------------------------------
    // client_credentials — fail-closed
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("client_credentials: missing separator in clientName → IllegalStateException (fail-closed)")
    void clientCredentials_missingSeparator_failsClosed() {
        RegisteredClient client = buildClient("no-separator");

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.CLIENT_CREDENTIALS);
        when(context.getRegisteredClient()).thenReturn(client);

        assertThatThrownBy(() -> customizer.customize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant_id is required");
    }

    @Test
    @DisplayName("client_credentials: null clientName → IllegalStateException (fail-closed)")
    void clientCredentials_nullClientName_failsClosed() {
        RegisteredClient client = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("test-client")
                .clientSecret("{noop}secret")
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.CLIENT_CREDENTIALS);
        when(context.getRegisteredClient()).thenReturn(client);

        assertThatThrownBy(() -> customizer.customize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant_id is required");
    }

    @Test
    @DisplayName("client_credentials: blank tenantId → IllegalStateException (fail-closed)")
    void clientCredentials_blankTenantId_failsClosed() {
        RegisteredClient client = buildClient("|B2C");

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.CLIENT_CREDENTIALS);
        when(context.getRegisteredClient()).thenReturn(client);

        assertThatThrownBy(() -> customizer.customize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant_id must not be blank");
    }

    @Test
    @DisplayName("client_credentials: blank tenantType → IllegalStateException (fail-closed)")
    void clientCredentials_blankTenantType_failsClosed() {
        RegisteredClient client = buildClient("fan-platform|");

        when(context.getTokenType()).thenReturn(OAuth2TokenType.ACCESS_TOKEN);
        when(context.getAuthorizationGrantType()).thenReturn(AuthorizationGrantType.CLIENT_CREDENTIALS);
        when(context.getRegisteredClient()).thenReturn(client);

        assertThatThrownBy(() -> customizer.customize(context))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant_type must not be blank");
    }

    // -----------------------------------------------------------------------
    // Non-access tokens — customizer must be a no-op
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("refresh token type → no-op (no claims injected, no exceptions)")
    void refreshToken_isNoOp() {
        when(context.getTokenType()).thenReturn(OAuth2TokenType.REFRESH_TOKEN);

        // Should not throw and should not call getAuthorizationGrantType()
        customizer.customize(context);
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private RegisteredClient buildClient(String clientName) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("test-internal-client")
                .clientSecret("{noop}secret")
                .clientName(clientName)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();
    }

    private JwtClaimsSet.Builder baseClaimsBuilder() {
        return JwtClaimsSet.builder()
                .issuer("http://localhost:8081")
                .subject("test-client")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(1800));
    }
}
