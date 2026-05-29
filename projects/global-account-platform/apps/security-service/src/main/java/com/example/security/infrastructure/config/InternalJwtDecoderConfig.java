package com.example.security.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

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
 * <p>This is a bare {@code JwtDecoder} bean — NOT an {@code oauth2ResourceServer} filter chain.
 * security-service has no Spring Security web chain and must not gain a default one; the decoder is
 * consumed directly by {@link InternalAuthFilter}.
 */
@Configuration
public class InternalJwtDecoderConfig {

    @Bean
    public JwtDecoder internalJwtDecoder(
            @Value("${security-service.internal-jwt.jwk-set-uri:http://localhost:8081/oauth2/jwks}") String jwkSetUri,
            @Value("${security-service.internal-jwt.issuer:http://localhost:8081}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }
}
