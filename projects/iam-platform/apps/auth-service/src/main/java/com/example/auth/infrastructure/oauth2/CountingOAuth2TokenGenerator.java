package com.example.auth.infrastructure.oauth2;

import com.example.auth.application.port.AuthMetricsPort;
import com.example.auth.application.port.AuthMetricsPort.IssuedTokenType;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

/**
 * TASK-BE-668 — counts {@code auth_token_issued_total{type}} at the Spring Authorization Server mint.
 *
 * <p><b>Why here.</b> auth-service has exactly two places that mint a token: the legacy
 * {@code TokenGeneratorPort} ({@code JwtTokenGenerator}, counted there) and SAS's
 * {@link OAuth2TokenGenerator} bean — which every SAS path goes through: the stock
 * {@code authorization_code} / {@code client_credentials} providers (they resolve the bean),
 * {@link SasRefreshTokenAuthenticationProvider} and {@link AssumeTenantAuthenticationProvider}
 * (both handed the same bean by {@link AuthorizationServerConfig}). Wrapping the bean counts
 * every grant once, with no per-path call to forget and no path counted twice — the two mints
 * never call each other.
 *
 * <p>Counted only when the delegate returned a token, and only for the two spec types: an
 * ID token ({@code id_token}) is neither {@code access} nor {@code refresh}, and a {@code null}
 * (a delegate declined, e.g. a client not allowed a refresh token) minted nothing.
 */
public final class CountingOAuth2TokenGenerator implements OAuth2TokenGenerator<OAuth2Token> {

    private final OAuth2TokenGenerator<? extends OAuth2Token> delegate;
    private final AuthMetricsPort authMetrics;

    public CountingOAuth2TokenGenerator(OAuth2TokenGenerator<? extends OAuth2Token> delegate,
                                        AuthMetricsPort authMetrics) {
        this.delegate = delegate;
        this.authMetrics = authMetrics;
    }

    @Override
    public OAuth2Token generate(OAuth2TokenContext context) {
        OAuth2Token token = delegate.generate(context);
        if (token != null) {
            OAuth2TokenType tokenType = context.getTokenType();
            if (OAuth2TokenType.ACCESS_TOKEN.equals(tokenType)) {
                authMetrics.tokenIssued(IssuedTokenType.ACCESS);
            } else if (OAuth2TokenType.REFRESH_TOKEN.equals(tokenType)) {
                authMetrics.tokenIssued(IssuedTokenType.REFRESH);
            }
        }
        return token;
    }
}
