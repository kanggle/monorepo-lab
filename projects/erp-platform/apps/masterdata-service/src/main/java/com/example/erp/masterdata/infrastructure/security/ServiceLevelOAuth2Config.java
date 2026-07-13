package com.example.erp.masterdata.infrastructure.security;

import com.example.erp.masterdata.presentation.security.PublicPaths;
import com.example.security.oauth2.AllowedIssuersValidator;
import com.example.security.oauth2.TenantClaimValidator;
import com.example.security.servlet.TenantClaimEnforcer;
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
 * Service-level Resource Server JWT decoder. Mirrors the erp
 * gateway-service validator chain so any direct call gets the same
 * {@link AllowedIssuersValidator} + {@link TenantClaimValidator} verdict
 * (architecture.md § Multi-tenancy — defense-in-depth). RS256 only (GAP JWKS).
 *
 * <p>The gateway <strong>exists</strong> as of TASK-MONO-357 (ADR-MONO-048 D7).
 * This chain is therefore no longer a stand-in for a missing edge — it is the
 * second layer, and it is load-bearing: the gateway only fronts traffic arriving
 * on the {@code erp.local} hostname, while anything already inside the compose
 * network (console-bff's outbound legs, service-to-service calls) reaches this
 * service directly and never crosses the edge. A request that skipped the gateway
 * must still meet the same verdict here. Keep this chain if the duplicated
 * validators are ever consolidated.
 */
@Configuration
public class ServiceLevelOAuth2Config {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${erpplatform.oauth2.allowed-issuers}")
    private String allowedIssuersCsv;

    @Value("${erpplatform.oauth2.required-tenant-id:erp}")
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
                .allowSuperAdminWildcard()   // SUPER_ADMIN platform scope (ADR-MONO-019 § D5)
                .trustEntitledDomains()      // entitlement-trust dual-accept
                .build());
        validators.add(JwtValidators.createDefault());
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    /**
     * erp's servlet tenant gate — the inner layer behind {@link #jwtTokenValidator()}.
     *
     * <p>An explicit {@code @Bean}, not a component scan. The copy this replaces was a
     * {@code @Component} in this service's own source tree, so its policy lived wherever that
     * file happened to be; a shared class annotated {@code @Component} would decide the same
     * policy somewhere nobody looks. The three relaxations below are the whole of erp's
     * deviation from the closed default, and they must match {@link #jwtTokenValidator()}
     * above — a decoder and an enforcer that disagree are not defence in depth (ADR-MONO-049
     * § 1.8 and § 1.9).
     */
    @Bean
    public TenantClaimEnforcer tenantClaimEnforcer() {
        return TenantClaimEnforcer.forTenant(requiredTenantId)
                .exempt(PublicPaths::isPublic)   // erp's own list: the actuator probes only
                .allowSuperAdminWildcard()
                .trustEntitledDomains()
                .build();
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
