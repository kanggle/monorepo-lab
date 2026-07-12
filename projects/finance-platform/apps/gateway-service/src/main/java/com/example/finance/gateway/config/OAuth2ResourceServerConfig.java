package com.example.finance.gateway.config;

import com.example.apigateway.security.GatewayJwtDecoders;
import com.example.apigateway.security.JwksHealthProbe;
import com.example.security.oauth2.TenantClaimValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Reactive Resource Server JWT decoder configuration (TASK-MONO-357).
 *
 * <p>The validator chain is assembled by {@link GatewayJwtDecoders#validatorChain} — shared with
 * the other gateways, so "which checks run, and in what order" has one definition rather than
 * five. What stays here is what is finance's to decide: the property keys, and
 * {@link #tenantGate()}.
 */
@Configuration
public class OAuth2ResourceServerConfig {

    private final String jwkSetUri;
    private final String allowedIssuersCsv;
    private final String requiredTenantId;

    /**
     * Constructor injection rather than {@code @Value} fields, so {@link #tenantGate()} can be
     * exercised by a unit test against the values this config really binds. An unresolvable
     * placeholder still fails the context at startup rather than defaulting to something
     * permissive.
     */
    public OAuth2ResourceServerConfig(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${financeplatform.oauth2.allowed-issuers}") String allowedIssuersCsv,
            @Value("${financeplatform.oauth2.required-tenant-id:finance}") String requiredTenantId) {
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
     * finance's tenant gate: equality <strong>or</strong> the {@code "*"} SUPER_ADMIN wildcard,
     * plus entitlement-trust dual-accept (ADR-MONO-019 § D5).
     *
     * <p><strong>This is not a new decision.</strong> It is the gate finance's own services
     * already declare — every {@code ServiceLevelOAuth2Config} under `finance-platform` builds
     * `new TenantClaimValidator(financeplatform.oauth2.required-tenant-id:finance)` with the
     * wildcard and entitlement branches. A gateway whose gate differed from the services behind
     * it would mean the edge and the service disagree about which tokens are valid — the edge
     * would reject tokens the service accepts, or wave through tokens it does not. Copying the
     * services' declared policy is the only choice here that is not an invention.
     *
     * <p>Test seam: {@code TenantClaimValidatorTest} builds its validator from <em>this</em>
     * method, so a change to the gate turns that suite red instead of silently moving the edge.
     */
    public TenantClaimValidator tenantGate() {
        return TenantClaimValidator.forTenant(requiredTenantId)
                .allowSuperAdminWildcard()
                .trustEntitledDomains()
                .build();
    }

    /**
     * Fails the gateway's boot fast when the IdP's JWKS endpoint is unreachable, instead of
     * letting the first real caller discover it as a 401.
     *
     * <p>The probe lives in {@code libs/java-gateway} and deliberately carries <strong>no
     * {@code @Component}</strong>: this gateway scans the library package, and so does wms —
     * which has never had a startup probe. Registration is opt-in, so the declaration lives here.
     */
    @Bean
    @ConditionalOnProperty(
            value = "gateway.jwks.startup-probe.enabled", havingValue = "true", matchIfMissing = true)
    public JwksHealthProbe jwksHealthProbe(
            @Value("${gateway.jwks.startup-probe.timeout-seconds:30}") long timeoutSeconds,
            ConfigurableApplicationContext applicationContext,
            WebClient.Builder webClientBuilder) {
        return new JwksHealthProbe(jwkSetUri, timeoutSeconds, applicationContext, webClientBuilder);
    }
}
