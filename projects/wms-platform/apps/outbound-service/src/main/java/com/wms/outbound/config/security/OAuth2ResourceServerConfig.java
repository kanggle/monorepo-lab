package com.wms.outbound.config.security;

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
 * <p>The decoder/validator <em>assembly</em> — {@code NimbusJwtDecoder.withJwkSetUri(...)},
 * the CSV parse, and the timestamp → allowed-issuers → tenant-gate → Spring-defaults
 * validator order — comes from {@link ResourceServerChainAssembler} since
 * ADR-MONO-058 § D4 (TASK-BE-569). The <strong>policy</strong> stays here: the property
 * keys, the issuer allow-list, and the tenant gate below.
 *
 * <p>{@link ResourceServerChainAssembler} installs nothing by itself — it is a plain
 * builder invoked from this {@code @Configuration}, not an auto-configuration, so this
 * file remains the only place outbound-service's resource-server posture is decided.
 */
@Configuration
public class OAuth2ResourceServerConfig {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${wms.oauth2.allowed-issuers}")
    private String allowedIssuersCsv;

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

    @Bean
    public OAuth2TokenValidator<Jwt> jwtTokenValidator() {
        return ResourceServerChainAssembler.jwtDecoder(jwkSetUri)
                .allowedIssuersCsv(allowedIssuersCsv)
                .validator(tenantGate())
                .buildValidator();
    }

    /**
     * The tenant gate, and the only place outbound-service's tenant policy is stated.
     *
     * <h2>{@code allowSuperAdminWildcard()} is deliberately NOT called</h2>
     *
     * wms is the <strong>only</strong> platform that rejects the SUPER_ADMIN {@code "*"}
     * wildcard (ADR-MONO-048 § D5). The validator builder defaults closed, so a
     * <em>forgotten</em> switch narrows the gate and something goes red, while an
     * <em>added</em> one widens it and nothing complains — which is why
     * {@code WmsTenantGatePolicyTest} asserts the refusal and not just the acceptance
     * (TASK-MONO-355 found this gate had zero coverage for its rejection).
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
