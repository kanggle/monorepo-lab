package com.example.auth.infrastructure.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.authentication.AuthenticationConverter;

/**
 * TASK-BE-327 (ADR-MONO-020 § 3.3 step 2, D2) — client-auth converter for the
 * public-client <b>assume-tenant token-exchange</b> grant
 * ({@code grant_type=urn:ietf:params:oauth:grant-type:token-exchange}).
 *
 * <p>Mirrors {@link PublicClientRefreshTokenAuthenticationConverter} verbatim in
 * shape: the {@code platform-console-web} SPA is a public client
 * ({@link ClientAuthenticationMethod#NONE}); the stock client-auth converters
 * reject its (client_id-only, no secret, no PKCE) request, so the request never
 * reaches the token endpoint. This converter authenticates the public client for
 * the token-exchange grant and emits an authenticated
 * {@link OAuth2ClientAuthenticationToken}, which the existing
 * {@link PublicClientNoPkceAuthenticationProvider} passes through (it accepts any
 * NONE-method already-authenticated client token).
 *
 * <p>Match conditions (ALL must hold; otherwise returns {@code null} so other
 * converters take over — existing flows untouched):
 * <ol>
 *   <li>{@code grant_type=urn:ietf:params:oauth:grant-type:token-exchange}</li>
 *   <li>{@code client_id} present</li>
 *   <li>No {@code Authorization} header (defer to confidential schemes)</li>
 *   <li>The registered client exists and supports {@link ClientAuthenticationMethod#NONE}</li>
 * </ol>
 */
public final class PublicClientTokenExchangeAuthenticationConverter implements AuthenticationConverter {

    private final RegisteredClientRepository registeredClientRepository;

    public PublicClientTokenExchangeAuthenticationConverter(
            RegisteredClientRepository registeredClientRepository) {
        this.registeredClientRepository = registeredClientRepository;
    }

    @Override
    public Authentication convert(HttpServletRequest request) {
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        if (!AuthorizationGrantType.TOKEN_EXCHANGE.getValue().equals(grantType)) {
            return null;
        }
        String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            return null;
        }
        RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
        if (registeredClient == null) {
            return null;
        }
        if (!registeredClient.getClientAuthenticationMethods()
                .contains(ClientAuthenticationMethod.NONE)) {
            return null;
        }
        return new OAuth2ClientAuthenticationToken(
                registeredClient,
                ClientAuthenticationMethod.NONE,
                null);
    }
}
