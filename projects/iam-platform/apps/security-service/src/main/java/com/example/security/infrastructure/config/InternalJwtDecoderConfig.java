package com.example.security.infrastructure.config;

import com.example.web.security.RequiredScopeValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.ArrayList;
import java.util.List;

/**
 * TASK-BE-317 (ADR-005 단계 2): {@link JwtDecoder} for verifying GAP-issued
 * {@code client_credentials} access tokens presented on {@code /internal/**}
 * (see {@link InternalAuthFilter}).
 *
 * <p>Validates signature against the GAP JWKS plus the standard timestamp checks and the GAP
 * issuer. Built from the JWKS URI directly (not the issuer's OIDC discovery document) so
 * application startup is not coupled to auth-service availability — the JWKS is fetched lazily on
 * first verification. security-service receives internal calls for accounts across all tenants, so
 * {@code tenant_id} is intentionally NOT pinned (unlike the community/membership resource servers).
 *
 * <p>TASK-MONO-422: additionally pins the {@code internal.invoke} workload scope via
 * {@link #internalTokenValidator(String, String)}. The GAP {@code auth-service} SAS is a single shared
 * issuer that mints both system ({@code client_credentials}) and user ({@code authorization_code})
 * tokens, so signature + issuer alone do not distinguish a system credential from a user token — the
 * shared {@link RequiredScopeValidator} rejects any token lacking {@code internal.invoke}, which
 * {@link InternalAuthFilter} surfaces as its existing 403 PERMISSION_DENIED (fail-closed).
 *
 * <p>This is a bare {@code JwtDecoder} bean — NOT an {@code oauth2ResourceServer} filter chain.
 * security-service has no Spring Security web chain and must not gain a default one; the decoder is
 * consumed directly by {@link InternalAuthFilter}.
 */
@Configuration
public class InternalJwtDecoderConfig {

    @Bean
    public JwtDecoder internalJwtDecoder(
            @Value("${security-service.internal-jwt.jwk-set-uri:http://localhost:8081/oauth2/jwks}") String jwkSetUri,
            @Value("${security-service.internal-jwt.issuer:http://localhost:8081}") String issuer,
            @Value("${security-service.internal-jwt.required-scope:internal.invoke}") String requiredScope) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(internalTokenValidator(issuer, requiredScope));
        return decoder;
    }

    /**
     * The validator chain enforced on {@code /internal/**} tokens: the issuer/timestamp default plus the
     * {@code internal.invoke} scope discriminator (TASK-MONO-422). {@code static} so a test can assert the
     * <em>actual</em> chain the decoder uses (not a re-implemented copy) — a scope-less token must be
     * rejected here. A blank {@code requiredScope} makes {@link RequiredScopeValidator} fail closed.
     */
    static OAuth2TokenValidator<Jwt> internalTokenValidator(String issuer, String requiredScope) {
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(JwtValidators.createDefaultWithIssuer(issuer));
        validators.add(new RequiredScopeValidator(requiredScope));
        return new DelegatingOAuth2TokenValidator<>(validators);
    }
}
