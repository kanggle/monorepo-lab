package com.wms.gateway.config;

import com.example.apigateway.security.GatewayJwtDecoders;
import com.example.apigateway.security.TenantClaimValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * Reactive Resource Server JWT decoder configuration (TASK-MONO-019).
 *
 * <p>The decoder accepts both legacy {@code POST /api/auth/login} tokens and SAS-issued
 * tokens during the deprecation window: the JWKS URI points at GAP, and {@code iss} is
 * checked against an explicit allowlist. The chain itself is assembled by
 * {@link GatewayJwtDecoders#validatorChain} — shared, so "which checks run and in what
 * order" has one definition rather than three.
 *
 * <p>What stays here is what is wms's to decide: the property keys, and
 * {@link #tenantGate()}.
 */
@Configuration
public class OAuth2ResourceServerConfig {

    private final String jwkSetUri;
    private final String allowedIssuersCsv;
    private final String requiredTenantId;

    /**
     * Constructor injection rather than {@code @Value} fields, so {@link #tenantGate()} can
     * be exercised by a unit test against the values this config really binds. The property
     * keys are unchanged, and so is the failure mode: an unresolvable placeholder still
     * fails the context at startup rather than defaulting to something permissive.
     */
    public OAuth2ResourceServerConfig(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${wms.oauth2.allowed-issuers}") String allowedIssuersCsv,
            @Value("${wms.oauth2.required-tenant-id:wms}") String requiredTenantId) {
        this.jwkSetUri = jwkSetUri;
        this.allowedIssuersCsv = allowedIssuersCsv;
        this.requiredTenantId = requiredTenantId;
    }

    @Bean
    @ConditionalOnMissingBean(ReactiveJwtDecoder.class)
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        return GatewayJwtDecoders.nimbus(jwkSetUri, jwtTokenValidator());
    }

    @Bean
    public OAuth2TokenValidator<Jwt> jwtTokenValidator() {
        return GatewayJwtDecoders.validatorChain(
                GatewayJwtDecoders.parseCsv(allowedIssuersCsv), tenantGate());
    }

    /**
     * wms's tenant gate: <strong>strict legacy equality, no {@code "*"} wildcard</strong>,
     * plus entitlement-trust dual-accept (ADR-MONO-019 § D5 / ADR-MONO-048 § D5).
     *
     * <p>wms alone rejects the SUPER_ADMIN wildcard that scm and fan accept. That is a
     * documented choice, not drift, and ADR-MONO-048 § D5 preserves it explicitly — whether
     * a platform operator should be able to reach the wms edge during an incident is a real
     * question, but changing the answer is a behaviour change and does not belong in an
     * extraction.
     *
     * <p>This is a test seam: {@code TenantClaimValidatorTest} builds its validator
     * from <em>this</em> method, so relaxing the gate here — adding
     * {@code .allowSuperAdminWildcard()} — turns that suite red instead of silently opening
     * the edge.
     */
    public TenantClaimValidator tenantGate() {
        return TenantClaimValidator.forTenant(requiredTenantId)
                .trustEntitledDomains()
                .build();
    }
}
