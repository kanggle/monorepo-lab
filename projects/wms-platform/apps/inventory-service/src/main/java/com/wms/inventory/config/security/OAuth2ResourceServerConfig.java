package com.wms.inventory.config.security;

import com.example.security.oauth2.AllowedIssuersValidator;
import com.example.security.oauth2.TenantClaimValidator;
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
 * Resource Server JWT decoder configuration (TASK-MONO-019).
 */
@Configuration
public class OAuth2ResourceServerConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${wms.oauth2.allowed-issuers}")
    private String allowedIssuersCsv;

    @Value("${wms.oauth2.required-tenant-id:wms}")
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
        validators.add(TenantClaimValidator.forTenant(requiredTenantId)
                // no .allowSuperAdminWildcard() — wms is the only platform that rejects the
                // SUPER_ADMIN "*" wildcard (ADR-MONO-048 § D5). The builder defaults closed,
                // so ADDING that switch widens the gate and nothing complains — which is why
                // WmsTenantGatePolicyTest asserts the refusal, not just the acceptance
                // (TASK-MONO-355 found this gate had zero coverage for its rejection).
                .trustEntitledDomains()   // entitlement-trust dual-accept (ADR-MONO-019 § D5)
                .build());
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
