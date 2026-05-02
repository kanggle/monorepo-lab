package com.example.community.infrastructure.config;

import com.example.community.infrastructure.security.AllowedIssuersValidator;
import com.example.community.infrastructure.security.TenantClaimValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.ArrayList;
import java.util.List;

/**
 * Resource Server JWT decoder configuration (TASK-BE-253).
 *
 * <p>Supports BOTH legacy {@code POST /api/auth/login} tokens and SAS-issued tokens
 * during the deprecation window: the JWKS URI points at GAP, but the {@code iss}
 * claim is validated against an explicit allowlist that includes both the
 * SAS issuer URL and the legacy {@code "global-account-platform"} string.
 *
 * <p>Tenant isolation: every accepted token must additionally carry
 * {@code tenant_id = fan-platform}. Cross-tenant tokens (e.g. {@code wms}) fail
 * validation here and surface as 401/403 to the caller.
 */
@Configuration
public class OAuth2ResourceServerConfig {

    /**
     * JWKS URI of the issuer. Configured from {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}.
     */
    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    /**
     * Comma-separated allowlist of acceptable {@code iss} claim values. The first
     * entry is typically the OIDC issuer URL (SAS); legacy issuers can be added
     * during deprecation windows.
     */
    @Value("${community.oauth2.allowed-issuers}")
    private String allowedIssuersCsv;

    /** Required {@code tenant_id} claim value. community-service is fan-platform-only. */
    @Value("${community.oauth2.required-tenant-id:fan-platform}")
    private String requiredTenantId;

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(jwtTokenValidator());
        return decoder;
    }

    @Bean
    public OAuth2TokenValidator<Jwt> jwtTokenValidator() {
        List<String> allowedIssuers = parseCsv(allowedIssuersCsv);
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        // Standard timestamp validator — exp / nbf / iat with default leeway.
        validators.add(new JwtTimestampValidator());
        // No JwtIssuerValidator: we accept either the SAS issuer or the legacy
        // "global-account-platform" string while D2-b deprecation is ongoing.
        validators.add(new AllowedIssuersValidator(allowedIssuers));
        validators.add(new TenantClaimValidator(requiredTenantId));
        // Add Spring's default validators (currently just timestamp, but future-proof).
        validators.add(JwtValidators.createDefault());
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    private static List<String> parseCsv(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null) return out;
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }
}
