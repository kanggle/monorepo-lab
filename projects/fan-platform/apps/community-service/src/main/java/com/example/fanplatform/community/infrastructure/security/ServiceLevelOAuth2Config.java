package com.example.fanplatform.community.infrastructure.security;

import com.example.fanplatform.community.presentation.security.PublicPaths;
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
 * Service-level Resource Server JWT decoder. Mirrors the fan-platform gateway
 * but runs inside the community-service so any direct call (gateway bypass)
 * still gets the same validator chain — {@link AllowedIssuersValidator} +
 * {@link TenantClaimValidator} (defense-in-depth, TASK-FAN-BE-002 §
 * Acceptance Criteria).
 *
 * <h2>ADR-MONO-058 § D4 — the assembly is shared, the policy is not</h2>
 *
 * The {@code NimbusJwtDecoder} construction, the {@code parseCsv} helper and the validator
 * <em>order</em> now come from {@link ResourceServerChainAssembler}. Everything that decides
 * <em>who gets in</em> stays here: the property keys, the issuer allow-list, and the
 * {@link TenantClaimValidator} switches below. The library is called explicitly from this
 * {@code @Configuration}; it registers nothing on its own.
 */
@Configuration
public class ServiceLevelOAuth2Config {

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${fanplatform.oauth2.allowed-issuers}")
    private String allowedIssuersCsv;

    @Value("${fanplatform.oauth2.required-tenant-id:fan-platform}")
    private String requiredTenantId;

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder() {
        // Declared as JwtDecoder, not NimbusJwtDecoder: the bean's advertised type is what
        // @ConditionalOnMissingBean(JwtDecoder.class) above and Spring Security's single-decoder
        // resolution in SecurityConfig both key on, and D4 does not change either.
        return decoderAssembly().build();
    }

    @Bean
    public OAuth2TokenValidator<Jwt> jwtTokenValidator() {
        return decoderAssembly().buildValidator();
    }

    /**
     * The one place this service's token policy is stated; both beans above are built from it.
     *
     * <p>The assembler installs, in this exact order, the {@code JwtTimestampValidator}, the
     * {@link AllowedIssuersValidator} over the allow-list below, the tenant validator handed to it
     * here, and finally {@code JwtValidators.createDefault()} — the same four the hand-written chain
     * listed, in the same order, because that order decides which {@code OAuth2Error}
     * {@code SecurityConfig}'s entry point sees first and therefore whether a rejection is a 401 or a
     * 403.
     *
     * <p>Private, so it is not a {@code @Bean} method: each caller gets its own (stateless,
     * structurally identical) chain, which is the shape
     * {@link ResourceServerChainAssembler.JwtDecoderBuilder} documents for a service that exposes the
     * decoder and the validator as two separate beans.
     */
    private ResourceServerChainAssembler.JwtDecoderBuilder decoderAssembly() {
        return ResourceServerChainAssembler.jwtDecoder(jwkSetUri)
                .allowedIssuersCsv(allowedIssuersCsv)
                .validator(TenantClaimValidator.forTenant(requiredTenantId)
                        .allowSuperAdminWildcard()   // SUPER_ADMIN platform scope (ADR-MONO-019 § D5)
                        // no .trustEntitledDomains() — fan is outside the entitlement plane
                        .build());
    }

    /**
     * fan's servlet tenant gate — the inner layer behind {@link #jwtTokenValidator()}.
     *
     * <p>An explicit {@code @Bean}, not a component scan: a shared class annotated
     * {@code @Component} would decide this service's policy somewhere nobody looks.
     *
     * <h2>{@code trustEntitledDomains()} is deliberately NOT called</h2>
     *
     * fan sits outside the entitlement plane — none of its four copies ever held an
     * {@code isEntitled} branch (measured: zero, fleet-wide). <strong>This is the first place in
     * the D5 series where a switch stays OFF</strong>, and it is what "every switch defaults
     * closed" was built for: adding {@code .trustEntitledDomains()} here would <em>widen</em>
     * fan's gate to honour a claim it has never honoured, and widening is the quiet direction.
     * The policy pin asserts the refusal, not just the acceptance (ADR-MONO-049 § 1.9,
     * TASK-MONO-387).
     *
     * <p>The exemption is {@code PublicPaths} only — the three actuator probes plus the
     * {@code /actuator/health/} subtree. <strong>community's copy reasoned about this axis
     * explicitly and refused the blanket {@code /actuator/} prefix that scm's services shipped</strong>
     * ("a blanket prefix would bypass the tenant gate for endpoints that may be added later …
     * we want a fail-closed posture there"). That judgement is preserved here, where it now
     * holds for the whole project rather than for whoever happened to read that one file.
     */
    @Bean
    public TenantClaimEnforcer tenantClaimEnforcer() {
        return TenantClaimEnforcer.forTenant(requiredTenantId)
                .exempt(PublicPaths::isPublic)
                .allowSuperAdminWildcard()
                // no .trustEntitledDomains() — see above
                .build();
    }
}
