package com.wms.master.config.security;

import com.example.security.oauth2.TenantClaimValidator;
import com.example.security.servlet.ResourceServerChainAssembler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Resource Server JWT decoder configuration (TASK-MONO-019).
 *
 * <p>Supports BOTH legacy {@code POST /api/auth/login} tokens and SAS-issued tokens
 * during the deprecation window: the JWKS URI points at GAP, but the {@code iss}
 * claim is validated against an explicit allowlist that includes both the
 * SAS issuer URL and the legacy {@code "iam"} string.
 *
 * <p>Tenant isolation: every accepted token must additionally carry
 * {@code tenant_id = wms}. Cross-tenant tokens (e.g. {@code fan-platform}) fail
 * validation here and surface as 403 {@code TENANT_FORBIDDEN} to the caller.
 *
 * <p>The decoder/validator <em>assembly</em> — {@code NimbusJwtDecoder.withJwkSetUri(...)},
 * the CSV parse, and the timestamp → allowed-issuers → tenant-gate → Spring-defaults
 * validator order — comes from {@link ResourceServerChainAssembler} since
 * ADR-MONO-058 § D4 (TASK-BE-569); the policy above and below stays here. The assembler
 * installs nothing by itself (plain builder, not an auto-configuration), so this file
 * remains the only place master-service's resource-server posture is decided.
 */
@Configuration
public class OAuth2ResourceServerConfig {

    /** JWKS URI of the issuer. Configured from {@code spring.security.oauth2.resourceserver.jwt.jwk-set-uri}. */
    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    /**
     * Comma-separated allowlist of acceptable {@code iss} claim values. The first
     * entry is typically the OIDC issuer URL (SAS); legacy issuers can be added
     * during deprecation windows.
     */
    @Value("${wms.oauth2.allowed-issuers}")
    private String allowedIssuersCsv;

    /** Required {@code tenant_id} claim value. master-service is wms-only. */
    @Value("${wms.oauth2.required-tenant-id:wms}")
    private String requiredTenantId;

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder() {
        return ResourceServerChainAssembler.jwtDecoder(jwkSetUri)
                .allowedIssuersCsv(allowedIssuersCsv)
                .validator(tenantGate())
                .build();
    }

    /**
     * The validator chain, exposed as its own bean because the pre-D4 wiring did —
     * callers that read the validator bean must keep being able to.
     *
     * <p>No {@code JwtIssuerValidator}: we accept either the SAS issuer or the legacy
     * {@code "iam"} string while D2-b deprecation is ongoing, which is what the
     * assembler's {@code allowedIssuersCsv(...)} allow-list expresses.
     */
    @Bean
    public OAuth2TokenValidator<Jwt> jwtTokenValidator() {
        return ResourceServerChainAssembler.jwtDecoder(jwkSetUri)
                .allowedIssuersCsv(allowedIssuersCsv)
                .validator(tenantGate())
                .buildValidator();
    }

    /**
     * The tenant gate, and the only place master-service's tenant policy is stated.
     *
     * <h2>{@code allowSuperAdminWildcard()} is deliberately NOT called</h2>
     *
     * wms is the <strong>only</strong> platform that rejects the SUPER_ADMIN {@code "*"}
     * wildcard that erp, fan, finance and scm accept. That is a documented choice rather
     * than drift ({@code ADR-MONO-048} § D5 preserves it explicitly), and it is also the
     * <em>quiet</em> direction: the builder defaults closed, so a forgotten switch narrows
     * the gate and reds a test, while an <em>added</em> one widens it and nothing complains.
     * {@code TASK-MONO-355} found this exact gate had zero coverage for its rejection.
     *
     * <p>{@code WmsTenantGatePolicyTest} therefore builds its subject from
     * {@link #jwtTokenValidator()} — the <em>production</em> chain — and asserts the
     * refusal, not just the acceptance; add {@code .allowSuperAdminWildcard()} here and
     * that suite goes red instead of the wms edge silently opening to a platform operator.
     *
     * <p>The same chain already runs at wms's own edge: see {@code gateway-service}'s
     * {@code OAuth2ResourceServerConfig#tenantGate()} (ADR-MONO-048 § D7).
     *
     * <p>Both beans above build their own instance from this method; the validators are
     * stateless, so the two chains are behaviourally identical to the single shared
     * instance the pre-D4 wiring produced.
     */
    private OAuth2TokenValidator<Jwt> tenantGate() {
        return TenantClaimValidator.forTenant(requiredTenantId)
                .trustEntitledDomains()   // entitlement-trust dual-accept (ADR-MONO-019 § D5)
                .build();
    }
}
