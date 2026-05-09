package com.example.auth.infrastructure.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PublicClientRevokeAuthenticationConverter}.
 *
 * <p>TASK-BE-272 / ADR-003 option A. The revoke endpoint converter does not
 * inspect {@code grant_type} (revoke is not a grant), only that the request
 * carries {@code client_id} for a registered NONE-method client without an
 * {@code Authorization} header.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class PublicClientRevokeAuthenticationConverterTest {

    private static final String PUBLIC_CLIENT_ID = "demo-spa-client";

    @Mock
    private RegisteredClientRepository registeredClientRepository;

    @Mock
    private HttpServletRequest request;

    private PublicClientRevokeAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        converter = new PublicClientRevokeAuthenticationConverter(registeredClientRepository);
    }

    // -----------------------------------------------------------------------
    // Match — happy path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("token + client_id + no Authorization + NONE method → authenticated OAuth2ClientAuthenticationToken")
    void matchingRequest_returnsAuthenticatedClientToken() {
        RegisteredClient publicClient = publicClient(PUBLIC_CLIENT_ID);
        when(request.getParameter(OAuth2ParameterNames.TOKEN)).thenReturn("opaque-token-value");
        when(request.getParameter(OAuth2ParameterNames.GRANT_TYPE)).thenReturn(null);
        when(request.getParameter(OAuth2ParameterNames.CLIENT_ID)).thenReturn(PUBLIC_CLIENT_ID);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);
        when(registeredClientRepository.findByClientId(PUBLIC_CLIENT_ID)).thenReturn(publicClient);

        Authentication result = converter.convert(request);

        assertThat(result).isInstanceOf(OAuth2ClientAuthenticationToken.class);
        OAuth2ClientAuthenticationToken token = (OAuth2ClientAuthenticationToken) result;
        assertThat(token.isAuthenticated()).isTrue();
        assertThat(token.getRegisteredClient()).isSameAs(publicClient);
        assertThat(token.getClientAuthenticationMethod()).isEqualTo(ClientAuthenticationMethod.NONE);
        assertThat(token.getCredentials()).isNull();
    }

    // -----------------------------------------------------------------------
    // Skip — missing token (eg /oauth2/authorize requests share the
    //                       no-Authorization + client_id shape but lack `token`)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("missing token parameter → null (so /oauth2/authorize is not auto-authenticated)")
    void missingToken_returnsNull() {
        when(request.getParameter(OAuth2ParameterNames.TOKEN)).thenReturn(null);

        Authentication result = converter.convert(request);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("grant_type present (i.e. /oauth2/token request) → null (defer to RT converter)")
    void grantTypePresent_returnsNull() {
        when(request.getParameter(OAuth2ParameterNames.TOKEN)).thenReturn("opaque-token-value");
        when(request.getParameter(OAuth2ParameterNames.GRANT_TYPE)).thenReturn("refresh_token");

        Authentication result = converter.convert(request);

        assertThat(result).isNull();
    }

    // -----------------------------------------------------------------------
    // Skip — missing or blank client_id
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("missing client_id → null")
    void missingClientId_returnsNull() {
        when(request.getParameter(OAuth2ParameterNames.TOKEN)).thenReturn("opaque-token-value");
        when(request.getParameter(OAuth2ParameterNames.GRANT_TYPE)).thenReturn(null);
        when(request.getParameter(OAuth2ParameterNames.CLIENT_ID)).thenReturn(null);

        Authentication result = converter.convert(request);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("blank client_id → null")
    void blankClientId_returnsNull() {
        when(request.getParameter(OAuth2ParameterNames.TOKEN)).thenReturn("opaque-token-value");
        when(request.getParameter(OAuth2ParameterNames.GRANT_TYPE)).thenReturn(null);
        when(request.getParameter(OAuth2ParameterNames.CLIENT_ID)).thenReturn("");

        Authentication result = converter.convert(request);

        assertThat(result).isNull();
    }

    // -----------------------------------------------------------------------
    // Skip — Authorization header present (confidential client flow)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Authorization header present → null (defer to stock client-secret-basic)")
    void authorizationHeaderPresent_returnsNull() {
        when(request.getParameter(OAuth2ParameterNames.TOKEN)).thenReturn("opaque-token-value");
        when(request.getParameter(OAuth2ParameterNames.GRANT_TYPE)).thenReturn(null);
        when(request.getParameter(OAuth2ParameterNames.CLIENT_ID)).thenReturn(PUBLIC_CLIENT_ID);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic Zm9vOmJhcg==");

        Authentication result = converter.convert(request);

        assertThat(result).isNull();
    }

    // -----------------------------------------------------------------------
    // Skip — unknown client / method != NONE
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("unknown client_id → null")
    void unknownClientId_returnsNull() {
        when(request.getParameter(OAuth2ParameterNames.TOKEN)).thenReturn("opaque-token-value");
        when(request.getParameter(OAuth2ParameterNames.GRANT_TYPE)).thenReturn(null);
        when(request.getParameter(OAuth2ParameterNames.CLIENT_ID)).thenReturn("does-not-exist");
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);
        when(registeredClientRepository.findByClientId("does-not-exist")).thenReturn(null);

        Authentication result = converter.convert(request);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("client registered but NONE method not allowed → null")
    void confidentialClient_returnsNull() {
        RegisteredClient confidentialClient = confidentialClient("confidential-client");
        when(request.getParameter(OAuth2ParameterNames.TOKEN)).thenReturn("opaque-token-value");
        when(request.getParameter(OAuth2ParameterNames.GRANT_TYPE)).thenReturn(null);
        when(request.getParameter(OAuth2ParameterNames.CLIENT_ID)).thenReturn("confidential-client");
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);
        when(registeredClientRepository.findByClientId("confidential-client"))
                .thenReturn(confidentialClient);

        Authentication result = converter.convert(request);

        assertThat(result).isNull();
    }

    // -----------------------------------------------------------------------
    // Helpers
    // -----------------------------------------------------------------------

    private RegisteredClient publicClient(String clientId) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .redirectUri("http://localhost:3000/callback")
                .scope("openid")
                .clientSettings(ClientSettings.builder().requireProofKey(true).build())
                .build();
    }

    private RegisteredClient confidentialClient(String clientId) {
        return RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId(clientId)
                .clientSecret("{noop}secret")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();
    }
}
