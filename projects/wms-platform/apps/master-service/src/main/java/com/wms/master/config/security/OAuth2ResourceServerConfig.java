package com.wms.master.config.security;

import com.example.security.oauth2.AllowedIssuersValidator;
import com.example.security.oauth2.TenantClaimValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.ArrayList;
import java.util.List;

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
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(jwtTokenValidator());
        return decoder;
    }

    /**
     * The tenant gate, and the only place master-service's policy is stated.
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
     * <p>{@code WmsTenantGatePolicyTest} therefore builds its subject from <em>this</em>
     * method and asserts the refusal, not just the acceptance — add
     * {@code .allowSuperAdminWildcard()} here and that suite goes red instead of the wms
     * edge silently opening to a platform operator.
     *
     * <p>The same chain already runs at wms's own edge: see {@code gateway-service}'s
     * {@code OAuth2ResourceServerConfig#tenantGate()} (ADR-MONO-048 § D7).
     */
    @Bean
    public OAuth2TokenValidator<Jwt> jwtTokenValidator() {
        List<String> allowedIssuers = parseCsv(allowedIssuersCsv);
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        // Standard timestamp validator — exp / nbf / iat with default leeway.
        validators.add(new JwtTimestampValidator());
        // No JwtIssuerValidator: we accept either the SAS issuer or the legacy
        // "iam" string while D2-b deprecation is ongoing.
        validators.add(new AllowedIssuersValidator(allowedIssuers));
        validators.add(TenantClaimValidator.forTenant(requiredTenantId)
                // no .allowSuperAdminWildcard() — see above; wms rejects it on purpose
                .trustEntitledDomains()   // entitlement-trust dual-accept (ADR-MONO-019 § D5)
                .build());
        // Add Spring's default validators (currently just timestamp, but future-proof).
        validators.add(JwtValidators.createDefault());
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    private static List<String> parseCsv(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null) return out;
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                out.add(trimmed);
            }
        }
        return out;
    }
}
