package com.example.fanplatform.gateway.config;

import com.example.apigateway.security.GatewayJwtDecoders;
import com.example.apigateway.security.JwksHealthProbe;
import com.example.security.oauth2.TenantClaimValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * Reactive Resource Server JWT decoder configuration.
 *
 * <p>The decoder accepts both SAS-issued tokens and the legacy {@code "iam"} issuer during
 * the D2-b deprecation window: the JWKS URI points at GAP, and {@code iss} is checked
 * against an explicit allowlist. The chain itself is assembled by
 * {@link GatewayJwtDecoders#validatorChain} — shared, so "which checks run and in what
 * order" has one definition rather than three.
 *
 * <p>What stays here is what is fan's to decide: the property keys, and
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
            @Value("${fanplatform.oauth2.allowed-issuers}") String allowedIssuersCsv,
            @Value("${fanplatform.oauth2.required-tenant-id:fan-platform}") String requiredTenantId) {
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
     * fan's tenant gate: equality <strong>or</strong> the {@code "*"} SUPER_ADMIN wildcard,
     * so platform operators can reach this edge for incident response
     * (TASK-FAN-BE-001 § Failure Scenarios).
     *
     * <p><strong>No entitlement branch</strong>, and that is correct rather than an omission
     * (ADR-MONO-048 § D5): fan is not in {@code ProductCatalog.ENTRIES}, {@code V0019} seeds
     * subscriptions only for wms/scm/erp/finance, and {@code fan-platform} is a
     * {@code B2C_CONSUMER} tenant. fan sits outside the entitlement plane entirely, so
     * {@code .trustEntitledDomains()} here would add a branch no token can ever take —
     * dead code inside a production security filter, added for symmetry's sake.
     *
     * <p>This is a test seam: {@code TenantClaimValidatorTest} builds its validator
     * from <em>this</em> method, so a change to the gate here turns that suite red instead
     * of silently altering the edge.
     */
    public TenantClaimValidator tenantGate() {
        return TenantClaimValidator.forTenant(requiredTenantId)
                .allowSuperAdminWildcard()
                .build();
    }

    /**
     * Fails the gateway's boot fast when the IdP's JWKS endpoint is unreachable, instead of
     * letting the first real caller discover it as a 401 (TASK-FAN-BE-001 § Edge Cases).
     *
     * <p>The probe moved to {@code libs/java-gateway} in TASK-MONO-357, and it deliberately
     * carries <strong>no {@code @Component}</strong>: this gateway scans the library package,
     * and so does wms — which has never had a startup probe. Registration is opt-in, so the
     * declaration lives here rather than in the library.
     */
    @Bean
    @ConditionalOnProperty(
            value = "gateway.jwks.startup-probe.enabled", havingValue = "true", matchIfMissing = true)
    public JwksHealthProbe jwksHealthProbe(
            @Value("${gateway.jwks.startup-probe.timeout-seconds:30}") long timeoutSeconds,
            org.springframework.context.ConfigurableApplicationContext applicationContext,
            org.springframework.web.reactive.function.client.WebClient.Builder webClientBuilder) {
        return new JwksHealthProbe(jwkSetUri, timeoutSeconds, applicationContext, webClientBuilder);
    }
}
