package com.example.auth.infrastructure.oauth2;

import com.example.auth.infrastructure.oauth2.PublicClientNonPkceAuthenticationConverter.PublicClientNonPkceAuthenticationToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Authentication provider for {@link PublicClientNonPkceAuthenticationToken} — public
 * PKCE clients hitting non-{@code authorization_code} endpoints (refresh_token grant,
 * /oauth2/revoke).
 *
 * <p>Validates the client exists and supports {@link ClientAuthenticationMethod#NONE},
 * then returns an authenticated token. Unlike SAS's stock {@code
 * PublicClientAuthenticationProvider}, it does <b>not</b> invoke {@code
 * CodeVerifierAuthenticator.authenticateRequired} — that authenticator returns false
 * for non-authorization_code grants and would short-circuit with {@code invalid_grant
 * code_verifier}, defeating the purpose of this provider.
 *
 * <p>The PKCE wrong-code_verifier guard is preserved in
 * {@link PublicClientNonPkceAuthenticationConverter} which deliberately returns
 * {@code null} when {@code code_verifier} is present — that path stays with the stock
 * converter / provider and the standard PKCE validation chain runs unchanged.
 *
 * <p>Registered ahead of the stock providers via the
 * {@code clientAuthentication().authenticationProviders()} Consumer hook so it runs
 * first when its specific token subtype is presented.
 *
 * <p>TASK-MONO-046-7 Cluster A.
 */
@Slf4j
public final class PublicClientNonPkceAuthenticationProvider implements AuthenticationProvider {

    private final RegisteredClientRepository registeredClientRepository;

    public PublicClientNonPkceAuthenticationProvider(
            RegisteredClientRepository registeredClientRepository) {
        if (registeredClientRepository == null) {
            throw new IllegalArgumentException("registeredClientRepository cannot be null");
        }
        this.registeredClientRepository = registeredClientRepository;
    }

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        PublicClientNonPkceAuthenticationToken request = (PublicClientNonPkceAuthenticationToken) authentication;
        String clientId = request.getPrincipal().toString();

        RegisteredClient registeredClient = registeredClientRepository.findByClientId(clientId);
        if (registeredClient == null) {
            log.debug("PublicClientNonPkceAuthenticationProvider: clientId={} not found", clientId);
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_CLIENT,
                    "Public client authentication failed: client_id=" + clientId + " is not registered.",
                    null));
        }

        if (!registeredClient.getClientAuthenticationMethods().contains(ClientAuthenticationMethod.NONE)) {
            log.debug("PublicClientNonPkceAuthenticationProvider: clientId={} does not support NONE auth method",
                    clientId);
            throw new OAuth2AuthenticationException(new OAuth2Error(
                    OAuth2ErrorCodes.INVALID_CLIENT,
                    "Public client authentication failed: client_id=" + clientId
                            + " is not configured for the 'none' authentication method.",
                    null));
        }

        log.trace("PublicClientNonPkceAuthenticationProvider: authenticated public client clientId={}",
                clientId);

        // Returning a fresh token marked authenticated. The OAuth2ClientAuthenticationToken
        // constructor that takes a RegisteredClient sets authenticated=true.
        //
        // CRITICAL (TASK-MONO-046-7 Cluster A cycle 2): forward the input token's
        // additionalParameters to the new authenticated token. SAS's stock
        // OAuth2RefreshTokenAuthenticationConverter / OAuth2TokenRevocationAuthenticationConverter
        // re-read grant-specific parameters (refresh_token, scope, token, etc.) from
        // OAuth2ClientAuthenticationToken.getAdditionalParameters() because the form body
        // was already consumed by the client-auth filter. Dropping these parameters here
        // makes the downstream converter NPE / fail to locate the refresh_token. The 2-arg
        // constructor that produced an empty additional-parameter map has been removed.
        return new PublicClientNonPkceAuthenticationToken(
                registeredClient, request.getAdditionalParameters());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return PublicClientNonPkceAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
