package com.example.scmplatform.procurement.infrastructure.security;

import com.example.scmplatform.procurement.presentation.security.PublicPaths;
import com.example.security.oauth2.AllowedIssuersValidator;
import com.example.security.oauth2.TenantClaimValidator;
import com.example.security.servlet.ResourceServerChainAssembler;
import com.example.security.servlet.TenantClaimEnforcer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Service-level Resource Server JWT decoder. Mirrors the scm-platform
 * gateway-service validator chain inside the procurement-service so any
 * direct call (gateway bypass) gets the same {@link AllowedIssuersValidator}
 * + {@link TenantClaimValidator} verdict (TASK-SCM-BE-002 § Acceptance
 * Criteria #8 — defense-in-depth).
 *
 * <p>The <em>assembly</em> — the {@code NimbusJwtDecoder} construction, the CSV parse, and the
 * fixed validator order — comes from {@link ResourceServerChainAssembler} (ADR-MONO-058 § D4).
 * It is an explicit call from this class, never a component-scanned or auto-configured bean:
 * a library that installed a resource-server chain on a classpath bump would change who can call
 * this service without a diff in this service (`platform/shared-library-policy.md`
 * § No context-wide annotations). Everything scm decides — the issuer allow-list, the tenant-claim
 * policy, the exempt paths, and the property keys they bind from — stays below.
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
        return decoderAssembly().build();
    }

    @Bean
    public OAuth2TokenValidator<Jwt> jwtTokenValidator() {
        return decoderAssembly().buildValidator();
    }

    /**
     * scm's decode-time policy, expressed once and consumed by both beans above.
     *
     * <p>The validator order ({@code JwtTimestampValidator} → {@code AllowedIssuersValidator} →
     * this tenant policy → {@code JwtValidators.createDefault()}) is fixed by the assembler and is
     * behaviour, not style: {@code SecurityConfig.extractOAuth2Error} picks the first
     * non-{@code invalid_token} error to decide 401-vs-403, so the order decides which response a
     * multiply-invalid token gets.
     */
    private ResourceServerChainAssembler.JwtDecoderBuilder decoderAssembly() {
        return ResourceServerChainAssembler.jwtDecoder(jwkSetUri)
                .allowedIssuersCsv(allowedIssuersCsv)
                .validator(TenantClaimValidator.forTenant(requiredTenantId)
                        .allowSuperAdminWildcard()   // SUPER_ADMIN platform scope (ADR-MONO-019 § D5)
                        .trustEntitledDomains()      // entitlement-trust dual-accept
                        .build());
    }

    /**
     * scm's servlet tenant gate — the inner layer behind {@link #jwtTokenValidator()}.
     *
     * <p>An explicit {@code @Bean}, not a component scan. The copy this replaces was a
     * {@code @Component} in this service's own source tree, so its policy lived wherever that
     * file happened to be; a shared class annotated {@code @Component} would decide the same
     * policy somewhere nobody looks. The three relaxations below are the whole of scm's
     * deviation from the closed default, and they must match {@link #jwtTokenValidator()}
     * above — a decoder and an enforcer that disagree are not defence in depth (ADR-MONO-049
     * § 1.8 and § 1.9).
     *
     * <p>{@code PublicPaths} is the same list {@code SecurityConfig} permits, so the paths this
     * gate skips and the paths Spring Security lets through unauthenticated cannot drift apart.
     */
    @Bean
    public TenantClaimEnforcer tenantClaimEnforcer() {
        return TenantClaimEnforcer.forTenant(requiredTenantId)
                .exempt(PublicPaths::isPublic)   // actuator probes + the shared-secret webhooks
                .allowSuperAdminWildcard()
                .trustEntitledDomains()
                .build();
    }
}
