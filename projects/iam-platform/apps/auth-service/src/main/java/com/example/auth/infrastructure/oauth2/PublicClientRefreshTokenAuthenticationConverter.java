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
 * SAS {@link AuthenticationConverter} for the public-client {@code refresh_token} grant.
 *
 * <p>The stock {@code PublicClientAuthenticationConverter} only fires for
 * {@code authorization_code} requests carrying a {@code code_verifier} (PKCE). A
 * {@code refresh_token} grant from a public client (e.g. an SPA registered with
 * {@link ClientAuthenticationMethod#NONE}) does not satisfy that contract, so the
 * request reaches the SAS provider chain without an authenticated client and is
 * rejected with {@code invalid_client}. ADR-003 (option A) closes that gap by
 * introducing this dedicated converter.
 *
 * <p>Match conditions (ALL must hold; otherwise returns {@code null} so other
 * converters in the chain may take over):
 * <ol>
 *   <li>{@code grant_type=refresh_token}</li>
 *   <li>{@code client_id} parameter present</li>
 *   <li>No {@code Authorization} HTTP header (i.e. no client_secret_basic /
 *       client_secret_jwt being attempted simultaneously)</li>
 *   <li>The registered client exists and supports
 *       {@link ClientAuthenticationMethod#NONE}</li>
 * </ol>
 *
 * <p>Returns an authenticated {@link OAuth2ClientAuthenticationToken} so the
 * downstream {@code SasRefreshTokenAuthenticationProvider} can read the
 * {@link RegisteredClient} from the principal slot.
 *
 * <p><b>Anti-pattern compliance (PR #264 lessons):</b>
 * <ul>
 *   <li>A1 — no SAS shared-object lookup; this converter is constructed normally
 *       and only depends on {@link RegisteredClientRepository}.</li>
 *   <li>A2 — no token persistence: converter is auth-only.</li>
 *   <li>A3 — registered as a stateless Spring bean; no manual instantiation in
 *       the SAS configurer lambda.</li>
 *   <li>A4 — no shared static state; ordering-safe across IT classes.</li>
 * </ul>
 *
 * <p>TASK-BE-272 / ADR-003 option A.
 */
public final class PublicClientRefreshTokenAuthenticationConverter implements AuthenticationConverter {

    private final RegisteredClientRepository registeredClientRepository;

    public PublicClientRefreshTokenAuthenticationConverter(
            RegisteredClientRepository registeredClientRepository) {
        this.registeredClientRepository = registeredClientRepository;
    }

    @Override
    public Authentication convert(HttpServletRequest request) {
        // 1. grant_type filter — only refresh_token requests.
        String grantType = request.getParameter(OAuth2ParameterNames.GRANT_TYPE);
        if (!AuthorizationGrantType.REFRESH_TOKEN.getValue().equals(grantType)) {
            return null;
        }

        // 2. client_id required + no Authorization header (defer to stock
        //    converters when a confidential auth scheme is in play).
        String clientId = request.getParameter(OAuth2ParameterNames.CLIENT_ID);
        if (clientId == null || clientId.isBlank()) {
            return null;
        }
        if (request.getHeader(HttpHeaders.AUTHORIZATION) != null) {
            return null;
        }

        // 3. Lookup registered client + verify NONE method is allowed.
        RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
        if (registeredClient == null) {
            return null;
        }
        if (!registeredClient.getClientAuthenticationMethods()
                .contains(ClientAuthenticationMethod.NONE)) {
            return null;
        }

        // 4. Authenticated client token — credentials are null because public
        //    clients have no secret. The downstream SAS provider only reads the
        //    RegisteredClient off this token.
        return new OAuth2ClientAuthenticationToken(
                registeredClient,
                ClientAuthenticationMethod.NONE,
                null);
    }
}
