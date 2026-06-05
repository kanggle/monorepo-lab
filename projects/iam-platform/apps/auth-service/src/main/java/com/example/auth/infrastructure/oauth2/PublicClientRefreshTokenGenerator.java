package com.example.auth.infrastructure.oauth2;

import org.springframework.security.crypto.keygen.Base64StringKeyGenerator;
import org.springframework.security.crypto.keygen.StringKeyGenerator;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.time.Instant;
import java.util.Base64;

/**
 * Refresh-token generator that bypasses SAS's built-in {@code OAuth2RefreshTokenGenerator}
 * public-client exclusion so that PKCE-protected SPAs may receive refresh tokens.
 *
 * <h3>Why this exists</h3>
 * <p>SAS 1.4's stock {@code OAuth2RefreshTokenGenerator} returns {@code null} for any
 * authorization-code grant whose client authenticates with {@link
 * org.springframework.security.oauth2.core.ClientAuthenticationMethod#NONE NONE}
 * (i.e. public clients). That follows OAuth 2.1 BCP guidance, but our SPA flow
 * (TASK-BE-251 Phase 2b/2c) explicitly relies on refresh tokens being issued to
 * PKCE-protected public clients — and the {@link SasRefreshTokenAuthenticationProvider}
 * + {@link DomainSyncOAuth2AuthorizationService} pair is what gives us safe rotation
 * and reuse-detection on top of that.
 *
 * <p>This generator is registered as a {@code @Bean} of type
 * {@link OAuth2TokenGenerator OAuth2TokenGenerator&lt;OAuth2Token&gt;} alongside SAS's
 * default JWT/access-token generators. When SAS asks for a refresh token via
 * {@link OAuth2TokenType#REFRESH_TOKEN}, this generator runs first (we register it
 * inside our composite generator before SAS's stock RT generator).
 *
 * <p>The generator only handles refresh-token requests; for any other token type it
 * returns {@code null} so SAS's other generators can take over.
 *
 * <p>TASK-MONO-046-1 (Cluster A) — root-cause fix for "demo-spa-client receives no
 * refresh_token from authorization_code grant" because of the public-client exclusion.
 */
public class PublicClientRefreshTokenGenerator implements OAuth2TokenGenerator<OAuth2RefreshToken> {

    private final StringKeyGenerator refreshTokenGenerator =
            new Base64StringKeyGenerator(Base64.getUrlEncoder().withoutPadding(), 96);

    @Override
    public OAuth2RefreshToken generate(OAuth2TokenContext context) {
        if (!OAuth2TokenType.REFRESH_TOKEN.equals(context.getTokenType())) {
            return null;
        }

        RegisteredClient registeredClient = context.getRegisteredClient();
        Instant issuedAt = Instant.now();
        Instant expiresAt = issuedAt.plus(registeredClient.getTokenSettings().getRefreshTokenTimeToLive());

        return new OAuth2RefreshToken(this.refreshTokenGenerator.generateKey(), issuedAt, expiresAt);
    }
}
