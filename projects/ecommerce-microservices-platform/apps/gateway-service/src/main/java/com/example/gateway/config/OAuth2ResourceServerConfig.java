package com.example.gateway.config;

import com.example.apigateway.security.AllowedIssuersValidator;
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
     * ecommerce's tenant gate: <strong>any well-formed {@code tenant_id} is accepted</strong>;
     * only a missing / blank / non-string claim is rejected (as 403 {@code TENANT_FORBIDDEN}).
     *
     * <p>This is the only gate in the fleet that opens rather than narrows, and the reason is
     * structural rather than lax (ADR-MONO-030 § 2.4): ecommerce <em>is</em> the multi-tenant
     * marketplace — it serves every tenant, so "does this token's tenant match this gateway's
     * tenant?" is not a question it can ask. Entitlement is decided at IAM issuance time and
     * the edge trusts issuance; tenant separation is enforced one layer down, by the
     * persistence-layer {@code WHERE tenant_id} filter that the M6 cross-tenant-leak IT exists
     * to prove. "Any tenant" is still not "no tenant" — a blank claim would leave that filter
     * with nothing to filter on, so it fails here.
     *
     * <p>This is a test seam: {@code TenantClaimValidatorTest} builds its validator from
     * <em>this</em> method, and {@code TenantGatePolicyLeakTest} in the library asserts that
     * {@code acceptAnyWellFormedTenant} is <strong>off</strong> in wms, scm and fan — a switch
     * that opens an edge needs a test that says where it isn't.
     */
    public TenantClaimValidator tenantGate() {
        return TenantClaimValidator.forTenant(requiredTenantId)
                .acceptAnyWellFormedTenant()
                .build();
    }
}
