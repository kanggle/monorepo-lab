package com.example.fanplatform.community.infrastructure.security;

import com.example.fanplatform.community.presentation.security.PublicPaths;
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
 * Service-level Resource Server JWT decoder. Mirrors the fan-platform gateway
 * but runs inside the community-service so any direct call (gateway bypass)
 * still gets the same validator chain — {@link AllowedIssuersValidator} +
 * {@link TenantClaimValidator} (defense-in-depth, TASK-FAN-BE-002 §
 * Acceptance Criteria).
 */
@Configuration
public class ServiceLevelOAuth2Config {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${fanplatform.oauth2.allowed-issuers}")
    private String allowedIssuersCsv;

    @Value("${fanplatform.oauth2.required-tenant-id:fan-platform}")
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
                // no .trustEntitledDomains() — fan is outside the entitlement plane
                .build());
        validators.add(JwtValidators.createDefault());
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    /**
     * fan's servlet tenant gate — the inner layer behind {@link #jwtTokenValidator()}.
     *
     * <p>An explicit {@code @Bean}, not a component scan: a shared class annotated
     * {@code @Component} would decide this service's policy somewhere nobody looks.
     *
     * <h2>{@code trustEntitledDomains()} is deliberately NOT called</h2>
     *
     * fan sits outside the entitlement plane — none of its four copies ever held an
     * {@code isEntitled} branch (measured: zero, fleet-wide). <strong>This is the first place in
     * the D5 series where a switch stays OFF</strong>, and it is what "every switch defaults
     * closed" was built for: adding {@code .trustEntitledDomains()} here would <em>widen</em>
     * fan's gate to honour a claim it has never honoured, and widening is the quiet direction.
     * The policy pin asserts the refusal, not just the acceptance (ADR-MONO-049 § 1.9,
     * TASK-MONO-387).
     *
     * <p>The exemption is {@code PublicPaths} only — the three actuator probes plus the
     * {@code /actuator/health/} subtree. <strong>community's copy reasoned about this axis
     * explicitly and refused the blanket {@code /actuator/} prefix that scm's services shipped</strong>
     * ("a blanket prefix would bypass the tenant gate for endpoints that may be added later …
     * we want a fail-closed posture there"). That judgement is preserved here, where it now
     * holds for the whole project rather than for whoever happened to read that one file.
     */
    @Bean
    public TenantClaimEnforcer tenantClaimEnforcer() {
        return TenantClaimEnforcer.forTenant(requiredTenantId)
                .exempt(PublicPaths::isPublic)
                .allowSuperAdminWildcard()
                // no .trustEntitledDomains() — see above
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
