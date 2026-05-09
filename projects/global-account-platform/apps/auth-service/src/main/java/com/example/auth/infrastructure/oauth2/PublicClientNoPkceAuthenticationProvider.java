package com.example.auth.infrastructure.oauth2;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2ClientAuthenticationToken;

/**
 * Pass-through {@link AuthenticationProvider} that accepts the already-authenticated
 * {@link OAuth2ClientAuthenticationToken} produced by
 * {@link PublicClientRefreshTokenAuthenticationConverter} or
 * {@link PublicClientRevokeAuthenticationConverter}.
 *
 * <p>The stock {@code PublicClientAuthenticationProvider} runs the
 * {@code CodeVerifierAuthenticator} unconditionally, which throws
 * {@code invalid_grant} when no {@code code_verifier} parameter is present —
 * that is the case for the {@code refresh_token} grant and the public-client
 * {@code revoke} request, neither of which carry PKCE proof. Without a
 * dedicated provider, our converter's authenticated token is overwritten by
 * stock's PKCE-failure result.
 *
 * <p>This provider sits ahead of the stock chain (registered first via
 * {@code clientAuthentication().authenticationProvider(...)}) and short-circuits
 * the {@code AuthenticationManager} for tokens that match exactly the contract
 * our converters emit:
 * <ul>
 *   <li>{@link OAuth2ClientAuthenticationToken} with
 *       {@link ClientAuthenticationMethod#NONE}</li>
 *   <li>Already authenticated (i.e. {@code isAuthenticated() == true})</li>
 *   <li>Has a non-null {@code RegisteredClient}</li>
 * </ul>
 *
 * <p>Returns the same token unchanged. For any other token (e.g. stock
 * {@code PublicClientAuthenticationConverter} output for
 * {@code authorization_code + code_verifier}) we return {@code null} so the
 * stock provider runs.
 *
 * <p><b>Anti-pattern compliance (PR #264 lessons):</b>
 * <ul>
 *   <li>A1 — no SAS shared-object lookup; stateless.</li>
 *   <li>A2 — no token persistence, no DomainSync interaction.</li>
 *   <li>A3 — registered as a Spring-managed instance via the configurer; no
 *       AOP-sensitive logic.</li>
 *   <li>A4 — no static state; ordering-safe across IT classes.</li>
 * </ul>
 *
 * <p>TASK-BE-272 / ADR-003 option A.
 */
public final class PublicClientNoPkceAuthenticationProvider implements AuthenticationProvider {

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        if (!(authentication instanceof OAuth2ClientAuthenticationToken clientToken)) {
            return null;
        }
        // Only accept tokens our converters emit: NONE method + already
        // authenticated + RegisteredClient resolved.
        if (!ClientAuthenticationMethod.NONE.equals(clientToken.getClientAuthenticationMethod())) {
            return null;
        }
        if (!clientToken.isAuthenticated()) {
            return null;
        }
        if (clientToken.getRegisteredClient() == null) {
            return null;
        }
        // Pass through unchanged.
        return clientToken;
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return OAuth2ClientAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
