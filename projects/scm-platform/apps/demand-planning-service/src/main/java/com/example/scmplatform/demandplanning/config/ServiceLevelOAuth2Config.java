package com.example.scmplatform.demandplanning.config;

import com.example.scmplatform.demandplanning.adapter.inbound.web.security.PublicPaths;
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
 * Service-level JWT decoder, plus the servlet tenant gate that backs it up.
 *
 * <h2>These validators used to live here, as lambdas (ADR-MONO-049 § 1.10)</h2>
 *
 * Until {@code TASK-MONO-385} this class carried its own {@code tenantClaimValidator},
 * {@code allowedIssuersValidator} and {@code isEntitled} — the same logic every other service
 * held as a class, written inline. Fleet-wide only this service and {@code inventory-visibility}
 * did that, which is precisely why no count ever saw them: <strong>a detector that looks for
 * {@code public class TenantClaimValidator} cannot see a lambda.</strong>
 *
 * <p>Both layers still dual-accept (ADR-MONO-019 § D5), and now they cannot disagree, because
 * they are configured from the same switches a few lines apart (ADR-MONO-049 § 1.9).
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
        validators.add(new AllowedIssuersValidator(allowedIssuers));
        validators.add(TenantClaimValidator.forTenant(requiredTenantId)
                .allowSuperAdminWildcard()   // SUPER_ADMIN platform scope (ADR-MONO-019 § D5)
                .trustEntitledDomains()      // entitlement-trust dual-accept
                .build());
        validators.add(JwtValidators.createDefault());
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    /**
     * scm's servlet tenant gate — the inner layer behind {@link #jwtTokenValidator()}.
     *
     * <p>An explicit {@code @Bean}, not a component scan: a shared class annotated
     * {@code @Component} would decide this service's policy somewhere nobody looks.
     *
     * <p>{@code PublicPaths} is the same list {@code SecurityConfig} permits.
     * <strong>It did not used to be</strong> — the old filter exempted <em>all</em> of
     * {@code /actuator/} while SecurityConfig permitted three paths (ADR-MONO-049 § 1.8).
     */
    @Bean
    public TenantClaimEnforcer tenantClaimEnforcer() {
        return TenantClaimEnforcer.forTenant(requiredTenantId)
                .exempt(PublicPaths::isPublic)   // the three actuator probes — and nothing else
                .allowSuperAdminWildcard()
                .trustEntitledDomains()
                .build();
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
