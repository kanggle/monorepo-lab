package com.example.finance.account.infrastructure.security;

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
 * Service-level Resource Server JWT decoder. Mirrors the finance
 * gateway-service validator chain inside account-service so any direct call
 * gets the same {@link AllowedIssuersValidator} + {@link TenantClaimValidator}
 * verdict (architecture.md § Multi-tenancy — defense-in-depth). RS256 only
 * (GAP JWKS).
 *
 * <p>The gateway <strong>exists</strong> as of TASK-MONO-357 (ADR-MONO-048 D7).
 * This chain is therefore no longer a stand-in for a missing edge — it is the
 * second layer, and it is load-bearing: the gateway only fronts traffic arriving
 * on the {@code finance.local} hostname, while anything already inside the compose
 * network (console-bff's outbound legs, service-to-service calls) reaches this
 * service directly and never crosses the edge. A request that skipped the gateway
 * must still meet the same verdict here. Keep this chain if the duplicated
 * validators are ever consolidated.
 */
@Configuration
public class ServiceLevelOAuth2Config {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${financeplatform.oauth2.allowed-issuers}")
    private String allowedIssuersCsv;

    @Value("${financeplatform.oauth2.required-tenant-id:finance}")
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
