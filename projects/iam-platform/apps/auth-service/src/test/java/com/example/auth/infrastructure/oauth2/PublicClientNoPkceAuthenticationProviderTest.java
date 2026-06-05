package com.example.auth.infrastructure.oauth2;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PublicClientNoPkceAuthenticationProvider}.
 *
 * <p>TASK-BE-272 / ADR-003 option A. Verifies the pass-through behaviour for
 * already-authenticated tokens emitted by the public-client converters and
 * fall-through ({@code null} return) for tokens that should be handled by the
 * stock provider chain (e.g. unauthenticated NONE-method tokens carrying a
 * PKCE {@code code_verifier}).
 */
class PublicClientNoPkceAuthenticationProviderTest {

    private PublicClientNoPkceAuthenticationProvider provider;

    @BeforeEach
    void setUp() {
        provider = new PublicClientNoPkceAuthenticationProvider();
    }

    @Test
    @DisplayName("authenticated NONE-method client token → returned unchanged")
    void authenticatedNoneToken_passesThrough() {
        RegisteredClient client = publicClient();
        OAuth2ClientAuthenticationToken authenticated = new OAuth2ClientAuthenticationToken(
                client, ClientAuthenticationMethod.NONE, null);

        Authentication result = provider.authenticate(authenticated);

        assertThat(result).isSameAs(authenticated);
        assertThat(result.isAuthenticated()).isTrue();
    }

    @Test
    @DisplayName("unauthenticated NONE-method token (stock authorization_code + PKCE shape) → null (let stock provider run)")
    void unauthenticatedNoneToken_returnsNull() {
        // Stock PublicClientAuthenticationConverter emits an UNauthenticated token
        // carrying client_id + parameter map (with code_verifier). We must NOT
        // short-circuit it; the stock CodeVerifierAuthenticator must validate PKCE.
        OAuth2ClientAuthenticationToken unauthenticated = new OAuth2ClientAuthenticationToken(
                "demo-spa-client",
                ClientAuthenticationMethod.NONE,
                /* credentials */ null,
                java.util.Map.of("code_verifier", "abc"));

        Authentication result = provider.authenticate(unauthenticated);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("client-secret-basic token → null (not our concern)")
    void clientSecretBasicToken_returnsNull() {
        RegisteredClient client = confidentialClient();
        OAuth2ClientAuthenticationToken token = new OAuth2ClientAuthenticationToken(
                client, ClientAuthenticationMethod.CLIENT_SECRET_BASIC, "secret");

        Authentication result = provider.authenticate(token);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("non-OAuth2 token → null")
    void nonOAuth2Token_returnsNull() {
        Authentication result = provider.authenticate(
                new UsernamePasswordAuthenticationToken("user", "pass"));

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("supports OAuth2ClientAuthenticationToken")
    void supportsOAuth2ClientToken() {
        assertThat(provider.supports(OAuth2ClientAuthenticationToken.class)).isTrue();
    }

    @Test
    @DisplayName("does not support unrelated Authentication types")
    void doesNotSupportUsernamePassword() {
        assertThat(provider.supports(UsernamePasswordAuthenticationToken.class)).isFalse();
    }

    private RegisteredClient publicClient() {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("demo-spa-client")
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:3000/callback")
                .scope("openid")
                .clientSettings(ClientSettings.builder().requireProofKey(true).build())
                .build();
    }

    private RegisteredClient confidentialClient() {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("test-internal-client")
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();
    }
}
