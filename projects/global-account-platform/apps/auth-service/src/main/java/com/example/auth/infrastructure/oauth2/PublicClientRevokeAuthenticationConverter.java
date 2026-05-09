package com.example.auth.infrastructure.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.web.authentication.AuthenticationConverter;

/**
 * SAS {@link AuthenticationConverter} for public-client requests to
 * {@code POST /oauth2/revoke} (RFC 7009).
 *
 * <p>Mirrors {@link PublicClientRefreshTokenAuthenticationConverter} for the
 * revocation endpoint. The stock SAS revocation endpoint expects
 * client-secret-basic / client-secret-jwt, so a public client (e.g. an SPA
 * registered with {@link ClientAuthenticationMethod#NONE}) calling
 * {@code POST /oauth2/revoke} with only {@code client_id} + {@code token} would
 * otherwise be rejected with {@code invalid_client}.
 *
 * <p>Match conditions (ALL must hold; otherwise returns {@code null}):
 * <ol>
 *   <li>{@code token} parameter present (required for the revoke endpoint per
 *       RFC 7009 § 2.1; this is also the marker that distinguishes a revoke
 *       request from {@code /oauth2/authorize}, which would otherwise satisfy
 *       the public-client + client_id + no-auth-header pattern)</li>
 *   <li>{@code client_id} parameter present</li>
 *   <li>No {@code Authorization} HTTP header</li>
 *   <li>{@code grant_type} NOT present (further excludes /oauth2/token requests)</li>
 *   <li>The registered client exists and supports
 *       {@link ClientAuthenticationMethod#NONE}</li>
 * </ol>
 *
 * <p>{@code token} / {@code token_type_hint} parameters are consumed by the
 * downstream {@code OAuth2TokenRevocationAuthenticationProvider}.
 *
 * <p><b>Anti-pattern compliance:</b> identical to
 * {@link PublicClientRefreshTokenAuthenticationConverter} — auth-only, stateless,
 * no SAS shared-object lookup, no token persistence.
 *
 * <p>TASK-BE-272 / ADR-003 option A.
 */
public final class PublicClientRevokeAuthenticationConverter implements AuthenticationConverter {

    private final RegisteredClientRepository registeredClientRepository;

    public PublicClientRevokeAuthenticationConverter(
            RegisteredClientRepository registeredClientRepository) {
        this.registeredClientRepository = registeredClientRepository;
    }

    @Override
    public Authentication convert(HttpServletRequest request) {
        // 1. token parameter required — RFC 7009 § 2.1. This also acts as the
        //    /oauth2/revoke vs /oauth2/authorize discriminator: /oauth2/authorize
        //    shares the (NONE method + client_id + no Authorization header)
        //    shape with public-client revoke and would otherwise be matched here.
        String token = request.getParameter(OAuth2ParameterNames.TOKEN);
        if (token == null || token.isBlank()) {
            return null;
        }

        // 2. grant_type must be absent — /oauth2/token requests carry it; revoke
        //    does not. Defensive guard so the RT converter handles
        //    grant_type=refresh_token alone.
        if (request.getParameter(OAuth2ParameterNames.GRANT_TYPE) != null) {
            return null;
        }

        // 3. client_id required + no Authorization header (defer to stock
        //    confidential converters when client_secret_basic is in play).
        String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            return null;
        }

        // 4. Lookup registered client + verify NONE method is allowed.
        RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
        if (registeredClient == null) {
            return null;
        }
        if (!registeredClient.getClientAuthenticationMethods()
                .contains(ClientAuthenticationMethod.NONE)) {
            return null;
        }

        // 5. Authenticated client token (credentials = null for public clients).
        return new OAuth2ClientAuthenticationToken(
                registeredClient,
                ClientAuthenticationMethod.NONE,
                null);
    }
}
