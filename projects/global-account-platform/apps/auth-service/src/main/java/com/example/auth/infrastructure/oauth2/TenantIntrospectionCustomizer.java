package com.example.auth.infrastructure.oauth2;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.authorization.authentication.OAuth2TokenIntrospectionAuthenticationToken;
import org.springframework.security.oauth2.server.authorization.http.converter.OAuth2TokenIntrospectionHttpMessageConverter;
import org.springframework.security.oauth2.server.authorization.OAuth2TokenIntrospection;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Introspection response handler that enriches the standard RFC 7662 payload with
 * multi-tenant extension claims ({@code tenant_id}, {@code tenant_type}).
 *
 * <h3>Motivation</h3>
 * <p>RFC 7662 § 2.2 explicitly allows extension members in the introspection response.
 * The SAS default handler returns the standard fields ({@code active}, {@code sub},
 * {@code aud}, {@code iss}, {@code exp}, {@code iat}, {@code nbf}, {@code scope},
 * {@code client_id}, {@code username}, {@code token_type}). This handler augments
 * that response with {@code tenant_id} and {@code tenant_type} extracted from the
 * token's JWT claims (stored in {@link OAuth2TokenIntrospection}).
 *
 * <h3>Implementation Note</h3>
 * <p>SAS populates the {@link OAuth2TokenIntrospection} object from the introspected
 * JWT's claim set (via {@code JwtTokenIntrospector}). Custom claims present in the JWT
 * — such as {@code tenant_id} and {@code tenant_type} injected by
 * {@link TenantClaimTokenCustomizer} — are available in
 * {@link OAuth2TokenIntrospection#getClaims()}.
 *
 * <p>TASK-BE-251 Phase 2c — /oauth2/introspect with tenant extension claims.
 */
@Slf4j
public class TenantIntrospectionCustomizer implements AuthenticationSuccessHandler {

    private final HttpMessageConverter<OAuth2TokenIntrospection> introspectionHttpResponseConverter =
            new OAuth2TokenIntrospectionHttpMessageConverter();

    @Override
    public void onAuthenticationSuccess(
            HttpServletRequest request,
            HttpServletResponse response,
            Authentication authentication) throws IOException {

        OAuth2TokenIntrospectionAuthenticationToken introspectionAuthentication =
                (OAuth2TokenIntrospectionAuthenticationToken) authentication;

        OAuth2TokenIntrospection introspection = introspectionAuthentication.getTokenClaims();

        // Build extended introspection response with tenant claims
        OAuth2TokenIntrospection.Builder builder = buildBaseResponse(introspection);

        // Inject tenant_id and tenant_type from JWT claims (populated by TenantClaimTokenCustomizer)
        Map<String, Object> claims = introspection.getClaims();
        if (claims != null) {
            Object tenantId = claims.get("tenant_id");
            Object tenantType = claims.get("tenant_type");

            if (tenantId instanceof String tidStr && !tidStr.isBlank()) {
                builder.claim("tenant_id", tidStr);
            }
            if (tenantType instanceof String ttStr && !ttStr.isBlank()) {
                builder.claim("tenant_type", ttStr);
            }
        }

        OAuth2TokenIntrospection enrichedIntrospection = builder.build();

        log.debug("TenantIntrospectionCustomizer: enriched introspect response. " +
                "active={}, tenant_id={}, tenant_type={}",
                enrichedIntrospection.isActive(),
                enrichedIntrospection.getClaims().get("tenant_id"),
                enrichedIntrospection.getClaims().get("tenant_type"));

        ServletServerHttpResponse httpResponse = new ServletServerHttpResponse(response);
        this.introspectionHttpResponseConverter.write(enrichedIntrospection, null, httpResponse);
    }

    /**
     * Copies all standard OAuth2TokenIntrospection fields into a fresh builder.
     *
     * <p>{@link OAuth2TokenIntrospection#withClaims(Map)} already seeds the builder with
     * every claim present in the source map (including {@code aud}, {@code scope},
     * {@code iss}, etc.). Calling individual setter methods on top of that would trigger
     * double-insertion into SAS-internal lists that are already populated — causing
     * {@link UnsupportedOperationException} when those internal lists are unmodifiable.
     *
     * <p>Therefore: use {@code withClaims} as the sole copy mechanism, then only set
     * {@code active} which is a structural flag not represented in the raw claim map.
     */
    private OAuth2TokenIntrospection.Builder buildBaseResponse(OAuth2TokenIntrospection source) {
        // Seed builder from the raw claim map — copies aud, scope, iss, exp, iat, etc.
        Map<String, Object> claims = source.getClaims() != null ? source.getClaims() : Map.of();
        // Mutable copy so tenant claims can be injected before building
        Map<String, Object> mutableClaims = new HashMap<>(claims);

        OAuth2TokenIntrospection.Builder builder = OAuth2TokenIntrospection.withClaims(mutableClaims);

        // active is stored as a structural flag, not in the raw claim map
        builder.active(source.isActive());

        return builder;
    }
}
