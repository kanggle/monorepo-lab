package com.example.scmplatform.inventoryvisibility.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Service-level JWT decoder with allowed-issuers + tenant_id validators.
 * Mirrors procurement-service ServiceLevelOAuth2Config pattern.
 */
@Configuration
public class ServiceLevelOAuth2Config {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${scmplatform.oauth2.allowed-issuers}")
    private String allowedIssuersCsv;

    @Value("${scmplatform.oauth2.required-tenant-id:scm}")
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
        validators.add(new JwtTimestampValidator());
        validators.add(allowedIssuersValidator(allowedIssuers));
        validators.add(tenantClaimValidator(requiredTenantId));
        validators.add(JwtValidators.createDefault());
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    private static OAuth2TokenValidator<Jwt> allowedIssuersValidator(List<String> allowed) {
        return jwt -> {
            String iss = jwt.getIssuer() != null ? jwt.getIssuer().toString() : null;
            if (iss == null || allowed.stream().noneMatch(iss::equals)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "Issuer '" + iss + "' is not allowed", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    private static OAuth2TokenValidator<Jwt> tenantClaimValidator(String expectedTenantId) {
        Objects.requireNonNull(expectedTenantId, "expectedTenantId");
        return jwt -> {
            Object raw = jwt.getClaim("tenant_id");
            String tenantId = raw instanceof String s ? s : null;
            if (tenantId == null || tenantId.isBlank()) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "tenant_mismatch", "tenant_id claim is required", null));
            }
            if ("*".equals(tenantId)) return OAuth2TokenValidatorResult.success();
            if (!expectedTenantId.equals(tenantId)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "tenant_mismatch", "tenant_id '" + tenantId + "' is not allowed", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    private static List<String> parseCsv(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null) return out;
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }
}
