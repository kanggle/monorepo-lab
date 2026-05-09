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
 * Unit tests for {@link PublicClientRefreshTokenAuthenticationConverter}.
 *
 * <p>TASK-BE-272 / ADR-003 option A. Verifies that the converter fires only for
 * the public-client {@code refresh_token} grant pattern and otherwise returns
 * {@code null} so the SAS converter chain can fall through to other handlers.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class PublicClientRefreshTokenAuthenticationConverterTest {

    private static final String PUBLIC_CLIENT_ID = "demo-spa-client";

    @Mock
    private RegisteredClientRepository registeredClientRepository;

    @Mock
    private HttpServletRequest request;

    private PublicClientRefreshTokenAuthenticationConverter converter;

    @BeforeEach
    void setUp() {
        converter = new PublicClientRefreshTokenAuthenticationConverter(registeredClientRepository);
    }

    // -----------------------------------------------------------------------
    // Match — happy path
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("matching grant_type + client_id + NONE method → authenticated OAuth2ClientAuthenticationToken")
    void matchingRequest_returnsAuthenticatedClientToken() {
        RegisteredClient publicClient = publicClient(PUBLIC_CLIENT_ID);
        when(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))
                .thenReturn(AuthorizationGrantType.REFRESH_TOKEN.getValue());
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
    // Skip — non-matching grant_type
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("grant_type=authorization_code → null (let stock converter handle)")
    void nonMatchingGrantType_returnsNull() {
        when(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))
                .thenReturn(AuthorizationGrantType.AUTHORIZATION_CODE.getValue());

        Authentication result = converter.convert(request);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("grant_type missing → null")
    void missingGrantType_returnsNull() {
        when(request.getParameter(OAuth2ParameterNames.GRANT_TYPE)).thenReturn(null);

        Authentication result = converter.convert(request);

        assertThat(result).isNull();
    }

    // -----------------------------------------------------------------------
    // Skip — missing or blank client_id
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("missing client_id → null (defer to stock converters that may throw INVALID_CLIENT)")
    void missingClientId_returnsNull() {
        when(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))
                .thenReturn(AuthorizationGrantType.REFRESH_TOKEN.getValue());
        when(request.getParameter(OAuth2ParameterNames.CLIENT_ID)).thenReturn(null);

        Authentication result = converter.convert(request);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("blank client_id → null")
    void blankClientId_returnsNull() {
        when(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))
                .thenReturn(AuthorizationGrantType.REFRESH_TOKEN.getValue());
        when(request.getParameter(OAuth2ParameterNames.CLIENT_ID)).thenReturn("   ");

        Authentication result = converter.convert(request);

        assertThat(result).isNull();
    }

    // -----------------------------------------------------------------------
    // Skip — Authorization header present (confidential client flow)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Authorization header present → null (defer to client-secret-basic converter)")
    void authorizationHeaderPresent_returnsNull() {
        when(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))
                .thenReturn(AuthorizationGrantType.REFRESH_TOKEN.getValue());
        when(request.getParameter(OAuth2ParameterNames.CLIENT_ID)).thenReturn(PUBLIC_CLIENT_ID);
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn("Basic Zm9vOmJhcg==");

        Authentication result = converter.convert(request);

        assertThat(result).isNull();
    }

    // -----------------------------------------------------------------------
    // Skip — registered client absent or method != NONE
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("unknown client_id → null (chain returns INVALID_CLIENT downstream)")
    void unknownClientId_returnsNull() {
        when(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))
                .thenReturn(AuthorizationGrantType.REFRESH_TOKEN.getValue());
        when(request.getParameter(OAuth2ParameterNames.CLIENT_ID)).thenReturn("does-not-exist");
        when(request.getHeader(HttpHeaders.AUTHORIZATION)).thenReturn(null);
        when(registeredClientRepository.findByClientId("does-not-exist")).thenReturn(null);

        Authentication result = converter.convert(request);

        assertThat(result).isNull();
    }

    @Test
    @DisplayName("client registered but NONE method not allowed → null (let stock confidential converter run)")
    void confidentialClient_returnsNull() {
        RegisteredClient confidentialClient = confidentialClient("confidential-client");
        when(request.getParameter(OAuth2ParameterNames.GRANT_TYPE))
                .thenReturn(AuthorizationGrantType.REFRESH_TOKEN.getValue());
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
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN)
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS)
                .build();
    }
}
