package com.example.gateway.config;

import com.example.security.oauth2.AllowedIssuersValidator;
import com.example.apigateway.security.GatewayJwtDecoders;
import com.example.security.oauth2.TenantClaimValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;

/**
 * Reactive Resource Server JWT decoder configuration (TASK-MONO-027).
 *
 * <p>Spring Cloud Gateway runs on WebFlux, so the gateway publishes a
 * {@link ReactiveJwtDecoder} (not the servlet-style {@code JwtDecoder}). The decoder accepts
 * tokens issued by GAP under either the SAS issuer URL or the legacy {@code "iam"} string
 * during the deprecation window: the JWKS URI points at GAP, and {@code iss} is checked
 * against an explicit allowlist via {@link AllowedIssuersValidator}. The chain is assembled
 * by {@link GatewayJwtDecoders#validatorChain} — shared with the other three gateways, so
 * "which checks run and in what order" has one definition rather than four (TASK-MONO-356).
 *
 * <p>Conditional registration ({@link ConditionalOnMissingBean}) lets integration tests
 * override the decoder with a JWKS pointing at a MockWebServer instance (see
 * {@code GatewayIntegrationTest}).
 */
@Configuration
public class OAuth2ResourceServerConfig {

    private final String jwkSetUri;
    private final String allowedIssuersCsv;
    private final String requiredTenantId;

    /**
     * Constructor injection rather than {@code @Value} fields, so {@link #tenantGate()} can be
     * exercised by a unit test against the values this config really binds. The property keys
     * are unchanged, and so is the failure mode: an unresolvable placeholder still fails the
     * context at startup rather than defaulting to something permissive.
     */
    public OAuth2ResourceServerConfig(
            @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}") String jwkSetUri,
            @Value("${ecommerce.oauth2.allowed-issuers}") String allowedIssuersCsv,
            @Value("${ecommerce.oauth2.required-tenant-id:ecommerce}") String requiredTenantId) {
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
     * ecommerce's tenant gate — <strong>the fleet's gate</strong>: an exact {@code tenant_id}
     * match, the SUPER_ADMIN wildcard, or a GAP-signed {@code entitled_domains} claim naming
     * this domain. Anything else is rejected (as 403 {@code TENANT_FORBIDDEN}).
     *
     * <p>ecommerce is the multi-tenant marketplace (ADR-MONO-030 § D1-A), so this edge must
     * admit tokens whose {@code tenant_id} names some <em>other</em> tenant — a customer-tenant
     * operator running their own store carries {@code tenant_id=<their tenant>}. That is exactly
     * what {@link TenantClaimValidator.Builder#trustEntitledDomains() entitlement-trust} is for,
     * and it is how erp, finance, scm and wms have always reached their edges
     * (ADR-MONO-019 § D5). ADR-MONO-030 D1-A said so in as many words: ecommerce becomes the
     * <em>sixth entitlement-trust domain</em>.
     *
     * <p><strong>It never was one, until now.</strong> This gate used to call
     * {@code acceptAnyWellFormedTenant()} — admit any non-blank {@code tenant_id} — which is a
     * <em>weaker</em> question than entitlement asks. "Is this tenant entitled to ecommerce?"
     * became "does this token name a tenant at all?", so a token entitled only to some other
     * domain walked in (TASK-BE-506). The switch is gone from the library; ecommerce asks the
     * same question as everyone else (TASK-MONO-388).
     *
     * <p>Shoppers do not carry {@code entitled_domains} — they are consumers, not subscribers
     * (ADR-MONO-030 § D4-A) — and they do not need to: their {@code tenant_id} is
     * {@code ecommerce}, so they pass on the exact match. <strong>Entitlement-trust is the
     * operator path, not the shopper path.</strong> Do not "fix" a shopper failure by issuing
     * them an {@code entitled_domains} claim.
     *
     * <p>Tenant separation below the edge is unchanged: the persistence layer's
     * {@code WHERE tenant_id} filter, which the M6 cross-tenant-leak IT exists to prove.
     *
     * <p>This is a test seam: {@code TenantClaimValidatorTest} builds its validator from
     * <em>this</em> method, so changing the gate here turns those assertions red.
     */
    public TenantClaimValidator tenantGate() {
        return TenantClaimValidator.forTenant(requiredTenantId)
                .allowSuperAdminWildcard()
                .trustEntitledDomains()
                .build();
    }
}
