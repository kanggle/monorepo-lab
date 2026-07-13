package com.example.membership.infrastructure.config;

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
 * Resource Server JWT decoder configuration (TASK-BE-253).
 *
 * <p>Both legacy {@code POST /api/auth/login} tokens and SAS-issued tokens are
 * accepted while the deprecation window is open. Tenant isolation is enforced
 * via {@link TenantClaimValidator}: only {@code fan-platform} tokens pass.
 */
@Configuration
public class OAuth2ResourceServerConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${membership.oauth2.allowed-issuers}")
    private String allowedIssuersCsv;

    @Value("${membership.oauth2.required-tenant-id:fan-platform}")
    private String requiredTenantId;

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(jwtTokenValidator());
        return decoder;
    }

    /**
     * The tenant gate, and the only place membership-service's policy is stated.
     *
     * <h2>Both switches are deliberately left OFF</h2>
     *
     * iam is the <strong>only</strong> project in the fleet that calls neither
     * {@code allowSuperAdminWildcard()} nor {@code trustEntitledDomains()} — the strictest
     * tenant gate we have. Measured, not assumed (<code>ADR-MONO-049</code> § 1.12).
     *
     * <p>The builder defaults every switch closed, so <em>forgetting</em> one is not a
     * reachable mistake here. The only way to get this wrong is to <em>add</em> a switch out
     * of habit — which <strong>widens</strong> the gate, and widening is the quiet direction.
     * {@code IamTenantGatePolicyTest} asserts <strong>both refusals</strong> against this
     * method, so either habit turns the suite red.
     */
    @Bean
    public OAuth2TokenValidator<Jwt> jwtTokenValidator() {
        List<String> allowedIssuers = parseCsv(allowedIssuersCsv);
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        validators.add(new AllowedIssuersValidator(allowedIssuers));
        validators.add(TenantClaimValidator.forTenant(requiredTenantId)
                // no .allowSuperAdminWildcard(), no .trustEntitledDomains() — see above
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
