package com.example.scmplatform.logistics.config;

import com.example.scmplatform.logistics.adapter.inbound.web.security.PublicPaths;
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
 * Service-level JWT decoder plus the servlet tenant gate that backs it up — the SCM-BE-019
 * blueprint, wired identically to demand-planning / procurement (ADR-MONO-049 § D5).
 *
 * <p>Both layers <b>dual-accept</b> (ADR-MONO-019 § D5): a token passes if its legacy
 * {@code tenant_id ∈ {scm, *}} OR its signed {@code entitled_domains} contains {@code scm}
 * (entitlement-trust). Reject = {@code !legacyOk && !entitled}, fail-closed. Configured from the
 * same switches a few lines apart so the decode-time validator and the servlet enforcer cannot
 * disagree.
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
     * <p>The {@code NimbusJwtDecoder} construction, the CSV parse and the validator order come
     * from {@link ResourceServerChainAssembler} (ADR-MONO-058 § D4) — an explicit call, never a
     * component-scanned or auto-configured bean (`platform/shared-library-policy.md`
     * § No context-wide annotations). The order ({@code JwtTimestampValidator} →
     * {@link AllowedIssuersValidator} → this tenant policy → {@code JwtValidators.createDefault()})
     * is behaviour, not style: {@code SecurityConfig.extractOAuth2Error} picks the first
     * non-{@code invalid_token} error to decide 401-vs-403.
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
     * scm's servlet tenant gate — the inner layer behind {@link #jwtTokenValidator()}. An
     * explicit {@code @Bean} (not a component scan), exempting only the {@code PublicPaths}
     * probes, dual-accepting the same way as the decoder.
     */
    @Bean
    public TenantClaimEnforcer tenantClaimEnforcer() {
        return TenantClaimEnforcer.forTenant(requiredTenantId)
                .exempt(PublicPaths::isPublic)
                .allowSuperAdminWildcard()
                .trustEntitledDomains()
                .build();
    }
}
