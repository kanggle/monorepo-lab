package com.example.auth.infrastructure.oauth;

import com.example.auth.domain.oauth.OAuthProvider;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@ConfigurationProperties(prefix = "oauth")
public class OAuthProperties {

    private ProviderProperties google = new ProviderProperties();
    private ProviderProperties kakao = new ProviderProperties();
    private ProviderProperties microsoft = new ProviderProperties();
    private ProviderProperties naver = new ProviderProperties();

    /**
     * TASK-BE-623: whether {@code provider} has real credentials configured, i.e. the
     * single "provider configured" predicate the login page and the direct
     * {@code GET /login/oauth/{provider}} entry point both use. Lives here — not in the
     * Thymeleaf template, not in the controller — so the "what counts as configured"
     * rule has exactly one home ({@link ProviderProperties#isConfigured()}).
     */
    public boolean isConfigured(OAuthProvider provider) {
        return properties(provider).isConfigured();
    }

    private ProviderProperties properties(OAuthProvider provider) {
        return switch (provider) {
            case GOOGLE -> google;
            case KAKAO -> kakao;
            case MICROSOFT -> microsoft;
            case NAVER -> naver;
        };
    }

    @Getter
    @Setter
    public static class ProviderProperties {
        private String clientId;
        private String clientSecret;
        private String redirectUri = "http://localhost:3000/oauth/callback";
        /**
         * Server-side allowlist of redirect URIs accepted from the client. If empty,
         * {@link #redirectUri} is used as the single allowed value (backward
         * compatibility). Comparison is exact-string match — no normalization, no
         * prefix matching, no wildcard support, to prevent open-redirect bypasses.
         */
        private List<String> allowedRedirectUris = new ArrayList<>();
        private String scopes;
        private String tokenUri;
        private String authUri;
        private String userInfoUri;
        /**
         * JWKS endpoint URL for OIDC id_token signature verification (TASK-BE-145).
         * Empty/null disables JWKS-backed verification — only Kakao should leave
         * this unset since it does not return an id_token.
         */
        private String jwksUri;
        /**
         * Regex that the {@code iss} claim of a verified id_token must match.
         * Regex is used instead of an exact string so multi-tenant providers
         * (Microsoft tenant=common) can match issuer values that embed the
         * actual tenant identifier.
         */
        private String expectedIssuerPattern;
        /**
         * JWKS cache TTL in milliseconds. Default 10 minutes — short enough that a
         * provider-side key rotation propagates within one cache window, long
         * enough that JWKS endpoint load stays modest. Spec recommendation
         * (TASK-BE-145, implementation note line 117): 5–10 minutes for normal
         * caching; provider failure backoff is a separate 60-second guard inside
         * {@link OidcJwksVerifier}.
         */
        private long jwksCacheTtlMillis = 600_000L;

        public List<String> resolveAllowedRedirectUris() {
            if (allowedRedirectUris != null && !allowedRedirectUris.isEmpty()) {
                return List.copyOf(allowedRedirectUris);
            }
            return redirectUri != null ? List.of(redirectUri) : List.of();
        }

        /**
         * TASK-BE-623: "provider configured" = both {@code clientId} AND
         * {@code clientSecret} are present and are not the demo default
         * ({@code application.yml}'s {@code test-*-client-id} / {@code test-*-client-secret}
         * fallback). Checking only {@code clientId} would show a button for a half-real
         * configuration (real id, still-default secret) that cannot finish the token
         * exchange — the AC-2 control case. No format check on purpose: each provider's
         * real id/secret shape differs and may change, and pinning a format would make
         * the button silently disappear the day a provider changes it.
         */
        public boolean isConfigured() {
            return hasRealValue(clientId) && hasRealValue(clientSecret);
        }

        private static boolean hasRealValue(String value) {
            return value != null && !value.isBlank() && !value.startsWith("test-");
        }
    }
}
