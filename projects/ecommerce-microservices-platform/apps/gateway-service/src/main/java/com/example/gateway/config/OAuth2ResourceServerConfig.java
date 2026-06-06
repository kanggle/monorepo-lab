package com.example.gateway.config;

import com.example.gateway.security.AllowedIssuersValidator;
import com.example.gateway.security.TenantClaimValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusReactiveJwtDecoder;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

import java.util.ArrayList;
import java.util.List;

/**
 * Reactive Resource Server JWT decoder configuration (TASK-MONO-027).
 *
 * <p>Mirrors wms-platform's {@code OAuth2ResourceServerConfig} (TASK-MONO-019).
 * Spring Cloud Gateway runs on WebFlux, so the gateway must publish a
 * {@link ReactiveJwtDecoder} (not the servlet-style {@code JwtDecoder}). The
 * decoder accepts tokens issued by GAP under either the SAS issuer URL or the
 * legacy {@code "iam"} string during the deprecation
 * window: JWKS URI points at GAP, and the {@code iss} claim is validated
 * against an explicit allowlist via {@link AllowedIssuersValidator}.
 *
 * <p>Tenant isolation: every accepted token must additionally carry
 * {@code tenant_id = ecommerce}. Cross-tenant tokens (e.g. {@code wms},
 * {@code fan-platform}) fail validation here and surface as 403
 * {@code TENANT_FORBIDDEN} via {@link TenantClaimValidator}.
 *
 * <p>Conditional registration ({@link ConditionalOnMissingBean}) lets
 * integration tests override the decoder with a JWKS pointing at a
 * MockWebServer instance (see {@code GatewayIntegrationTest}).
 */
@Configuration
public class OAuth2ResourceServerConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${ecommerce.oauth2.allowed-issuers}")
    private String allowedIssuersCsv;

    @Value("${ecommerce.oauth2.required-tenant-id:ecommerce}")
    private String requiredTenantId;

    @Bean
    @ConditionalOnMissingBean(ReactiveJwtDecoder.class)
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        NimbusReactiveJwtDecoder decoder = NimbusReactiveJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(jwtTokenValidator());
        return decoder;
    }

    @Bean
    public OAuth2TokenValidator<Jwt> jwtTokenValidator() {
        List<String> allowedIssuers = parseCsv(allowedIssuersCsv);
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        validators.add(new AllowedIssuersValidator(allowedIssuers));
        validators.add(new TenantClaimValidator(requiredTenantId));
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
