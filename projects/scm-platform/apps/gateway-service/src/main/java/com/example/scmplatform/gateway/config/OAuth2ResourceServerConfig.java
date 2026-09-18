package com.example.scmplatform.gateway.config;

import com.example.security.oauth2.AudienceMode;
import com.example.security.oauth2.AllowedAudiencesValidator;
import com.example.apigateway.security.GatewayJwtDecoders;
import com.example.apigateway.security.JwksHealthProbe;
import com.example.security.oauth2.TenantClaimValidator;
import io.micrometer.core.instrument.MeterRegistry;
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
 * <p>What stays here is what is scm's to decide: the property keys, and
 * {@link #tenantGate()}.
 */
@Configuration
public class OAuth2ResourceServerConfig {

    /** Metric tag + log field for this edge's audience outcomes (TASK-MONO-696). */
    static final String GATEWAY_NAME = "scm";

    private final String jwkSetUri;
    private final String allowedIssuersCsv;
    private final String requiredTenantId;
    private final String allowedAudiencesCsv;
    private final String audienceMode;
    private final MeterRegistry meterRegistry;

    /**
     * Constructor injection rather than {@code @Value} fields, so {@link #tenantGate()} can
     * be exercised by a unit test against the values this config really binds. The property
     * keys are unchanged, and so is the failure mode: an unresolvable placeholder still
     * fails the context at startup rather than defaulting to something permissive.
     */
    public OAuth2ResourceServerConfig(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${scmplatform.oauth2.allowed-issuers}") String allowedIssuersCsv,
            @Value("${scmplatform.oauth2.required-tenant-id:scm}") String requiredTenantId,
            @Value("${scmplatform.oauth2.allowed-audiences}") String allowedAudiencesCsv,
            @Value("${scmplatform.oauth2.audience-mode}") String audienceMode,
            MeterRegistry meterRegistry) {
        this.jwkSetUri = jwkSetUri;
        this.allowedIssuersCsv = allowedIssuersCsv;
        this.requiredTenantId = requiredTenantId;
        this.allowedAudiencesCsv = allowedAudiencesCsv;
        this.audienceMode = audienceMode;
        this.meterRegistry = meterRegistry;
    }

    @Bean
    @ConditionalOnMissingBean(ReactiveJwtDecoder.class)
    public ReactiveJwtDecoder reactiveJwtDecoder() {
        return GatewayJwtDecoders.nimbus(jwkSetUri, jwtTokenValidator());
    }

    @Bean
    public OAuth2TokenValidator<Jwt> jwtTokenValidator() {
        return GatewayJwtDecoders.validatorChain(
                GatewayJwtDecoders.parseCsv(allowedIssuersCsv), audienceGate(), tenantGate());
    }

    /**
     * This edge's audience allowlist and mode ({@code jwt-standard-claims.md} rule 5,
     * TASK-MONO-696). Neither property has a {@code @Value} default, so an absent key fails the
     * context; an empty allowlist or an unknown mode fails construction. Startup failure either
     * way — never "accept any client".
     */
    public AllowedAudiencesValidator audienceGate() {
        return new AllowedAudiencesValidator(GATEWAY_NAME,
                GatewayJwtDecoders.parseCsv(allowedAudiencesCsv),
                AudienceMode.parse(audienceMode), meterRegistry);
    }

    /**
     * scm's tenant gate: equality <strong>or</strong> the {@code "*"} SUPER_ADMIN wildcard,
     * plus entitlement-trust dual-accept (ADR-MONO-019 § D5). The wildcard exists so a
     * platform operator can reach this edge during incident response
     * (TASK-SCM-BE-001 § Failure Scenarios).
     *
     * <p>This is a test seam: {@code TenantClaimValidatorTest} builds its validator
     * from <em>this</em> method, so a change to the gate here turns that suite red instead
     * of silently altering the edge.
     */
    public TenantClaimValidator tenantGate() {
        return TenantClaimValidator.forTenant(requiredTenantId)
                .allowSuperAdminWildcard()
                .trustEntitledDomains()
                .build();
    }

    /**
     * Fails the gateway's boot fast when the IdP's JWKS endpoint is unreachable, instead of
     * letting the first real caller discover it as a 401 (TASK-SCM-BE-001 § Edge Cases).
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
