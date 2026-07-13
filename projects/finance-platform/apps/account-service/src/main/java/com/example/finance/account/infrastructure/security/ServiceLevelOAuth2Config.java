package com.example.finance.account.infrastructure.security;

import com.example.finance.account.presentation.security.PublicPaths;
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
 * Service-level Resource Server JWT decoder, plus the servlet tenant gate that backs it up.
 * Mirrors the finance gateway-service verdict inside account-service so any direct call meets
 * the same gate (architecture.md § Multi-tenancy — defense-in-depth). RS256 only (IAM JWKS).
 *
 * <p>The gateway <strong>exists</strong> as of TASK-MONO-357 (ADR-MONO-048 D7). This chain is
 * therefore no longer a stand-in for a missing edge — it is the second layer, and it is
 * load-bearing: the gateway only fronts traffic arriving on the {@code finance.local} hostname,
 * while anything already inside the compose network (console-bff's outbound legs,
 * service-to-service calls) reaches this service directly and never crosses the edge. A request
 * that skipped the gateway must still meet the same verdict here.
 *
 * <h2>The three classes are shared now (ADR-MONO-049 § D5-3)</h2>
 *
 * {@link AllowedIssuersValidator}, {@link TenantClaimValidator} and {@link TenantClaimEnforcer}
 * used to be hand-maintained copies in this service. They now come from {@code libs/java-security}
 * and {@code libs/java-security-servlet}, and <strong>this file is where finance's tenant policy
 * is stated</strong> — every switch below defaults to <em>closed</em> in the shared classes, so
 * each relaxation finance relies on has to be named here, in the open, rather than being implied
 * by a copy nobody re-reads.
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
        validators.add(TenantClaimValidator.forTenant(requiredTenantId)
                .allowSuperAdminWildcard()   // SUPER_ADMIN platform scope (ADR-MONO-019 § D5)
                .trustEntitledDomains()      // entitlement-trust dual-accept (TASK-FIN-BE-006)
                .build());
        validators.add(JwtValidators.createDefault());
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    /**
     * finance's servlet tenant gate — the inner layer behind {@link #jwtTokenValidator()}.
     *
     * <p>Declared as an explicit {@code @Bean} rather than picked up by component scan. The
     * copy this replaces was a {@code @Component} in the service's own source tree, so its
     * policy was wherever that file happened to be; a shared class annotated {@code @Component}
     * would decide the same policy somewhere nobody looks. The three relaxations below are the
     * whole of finance's deviation from the closed default, and they must match
     * {@link #jwtTokenValidator()} above — a decoder and an enforcer that disagree are not
     * defence in depth (ADR-MONO-049 § 1.8, TASK-MONO-383).
     */
    @Bean
    public TenantClaimEnforcer tenantClaimEnforcer() {
        return TenantClaimEnforcer.forTenant(requiredTenantId)
                .exempt(PublicPaths::isPublic)   // finance's own list: the actuator probes only
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
