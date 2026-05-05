package com.example.auth.infrastructure.oauth2;

import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Validates a public-client refresh-token request created by
 * {@link PublicClientRefreshTokenAuthenticationConverter}.
 *
 * <p>The converter packages a {@code client_id} into an unauthenticated
 * {@link OAuth2ClientAuthenticationToken} with {@code ClientAuthenticationMethod.NONE}.
 * This provider:
 * <ol>
 *   <li>Looks up the {@link RegisteredClient} by client_id.</li>
 *   <li>Verifies the client exists and lists {@code "none"} in its
 *       {@code clientAuthenticationMethods} (i.e. it is a registered public client,
 *       not e.g. a confidential client trying to bypass authentication).</li>
 *   <li>Returns an authenticated {@code OAuth2ClientAuthenticationToken} so SAS's
 *       refresh-token authentication provider can proceed.</li>
 * </ol>
 *
 * <p>TASK-MONO-046-1 Cluster A — required for SPA refresh-token rotation.
 */
@Slf4j
public class PublicClientRefreshTokenAuthenticationProvider implements AuthenticationProvider {

    private final RegisteredClientRepository registeredClientRepository;

    public PublicClientRefreshTokenAuthenticationProvider(
            RegisteredClientRepository registeredClientRepository) {
        this.registeredClientRepository = registeredClientRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        OAuth2ClientAuthenticationToken token = (OAuth2ClientAuthenticationToken) authentication;
        if (!ClientAuthenticationMethod.NONE.equals(token.getClientAuthenticationMethod())) {
            return null;
        }
        // Only handle requests where principal is a String (client_id) — already-authenticated
        // tokens carry a RegisteredClient as principal and must pass through unchanged.
        if (!(token.getPrincipal() instanceof String clientId)) {
            return null;
        }

        RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
        if (registeredClient == null) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }
        if (!registeredClient.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)) {
            throw new OAuth2AuthenticationException(OAuth2ErrorCodes.INVALID_CLIENT);
        }

        return new OAuth2ClientAuthenticationToken(
                registeredClient,
                ClientAuthenticationMethod.NONE,
                null);
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
