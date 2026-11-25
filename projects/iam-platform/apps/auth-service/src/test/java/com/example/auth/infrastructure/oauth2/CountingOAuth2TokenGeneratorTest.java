package com.example.auth.infrastructure.oauth2;

import com.example.auth.infrastructure.metrics.AuthMetricsProbe;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.OAuth2Token;
import org.springframework.security.oauth2.core.oidc.endpoint.OidcParameterNames;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenType;
import org.springframework.security.oauth2.server.authorization.token.DefaultOAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenGenerator;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-668 — {@code auth_token_issued_total{type}} at the SAS mint: one count per minted
 * access / refresh token, nothing for an ID token or for a delegate that declined (null).
 */
class CountingOAuth2TokenGeneratorTest {

    private final AuthMetricsProbe metrics = new AuthMetricsProbe();

    private static final OAuth2Token TOKEN =
            new OAuth2RefreshToken("v", Instant.now(), Instant.now().plusSeconds(60));

    private static OAuth2TokenContext context(OAuth2TokenType type) {
        return DefaultOAuth2TokenContext.builder().tokenType(type).build();
    }

    private CountingOAuth2TokenGenerator generatorReturning(OAuth2Token token) {
        OAuth2TokenGenerator<OAuth2Token> delegate = ctx -> token;
        return new CountingOAuth2TokenGenerator(delegate, metrics.port());
    }

    @Test
    @DisplayName("a minted access token counts type=access; the delegate's token is returned unchanged")
    void accessTokenCounted() {
        OAuth2Token result = generatorReturning(TOKEN).generate(context(OAuth2TokenType.ACCESS_TOKEN));

        assertThat(result).isSameAs(TOKEN);
        assertThat(metrics.issued("access")).isEqualTo(1.0);
        assertThat(metrics.issued("refresh")).isZero();
    }

    @Test
    @DisplayName("a minted refresh token counts type=refresh")
    void refreshTokenCounted() {
        generatorReturning(TOKEN).generate(context(OAuth2TokenType.REFRESH_TOKEN));

        assertThat(metrics.issued("refresh")).isEqualTo(1.0);
        assertThat(metrics.issued("access")).isZero();
    }

    @Test
    @DisplayName("an ID token is neither access nor refresh — not counted")
    void idTokenNotCounted() {
        generatorReturning(TOKEN).generate(context(new OAuth2TokenType(OidcParameterNames.ID_TOKEN)));

        assertThat(metrics.issued("access")).isZero();
        assertThat(metrics.issued("refresh")).isZero();
    }

    @Test
    @DisplayName("a delegate that declines (null) minted nothing — not counted")
    void nullNotCounted() {
        OAuth2Token result = generatorReturning(null).generate(context(OAuth2TokenType.REFRESH_TOKEN));

        assertThat(result).isNull();
        assertThat(metrics.issued("refresh")).isZero();
    }
}
