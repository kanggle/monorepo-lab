package com.example.scmplatform.inventoryvisibility.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Service-level JWT decoder with allowed-issuers + tenant_id validators.
 * Mirrors procurement-service ServiceLevelOAuth2Config pattern.
 *
 * <p>The decode-time {@link #tenantClaimValidator(String)} applies the
 * <strong>entitlement-trust dual-accept</strong> gate (ADR-MONO-019 § D5,
 * ADR-MONO-020 § 3.3 D4): a token decodes when legacy
 * {@code tenant_id ∈ {expectedTenantId, "*"}} <em>or</em> the RS256/JWKS-
 * verified {@code entitled_domains} claim contains {@code expectedTenantId}.
 * Rejection requires <strong>both</strong> branches to fail (fail-closed —
 * entitlement only widens, never weakens). This mirrors the Layer-2
 * {@code TenantClaimEnforcer.isEntitled} helper; both authz layers
 * (decode validator + servlet filter) are independent gates and BOTH must
 * dual-accept, otherwise a domain-entitled cross-tenant token (e.g. globex
 * with {@code entitled_domains=[scm]}) is rejected at decode before the
 * filter's already-correct dual-accept can run (TASK-MONO-162).
 */
@Configuration
public class ServiceLevelOAuth2Config {

    private static final String CLAIM_ENTITLED_DOMAINS = "entitled_domains";

    @Value("${spring.security.oauth2.resourceserver.jwt.jwk-set-uri}")
    private String jwkSetUri;

    @Value("${scmplatform.oauth2.allowed-issuers}")
    private String allowedIssuersCsv;

    @Value("${scmplatform.oauth2.required-tenant-id:scm}")
    private String requiredTenantId;

    @Bean
    @ConditionalOnMissingBean(JwtDecoder.class)
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();
        decoder.setJwtValidator(jwtTokenValidator());
        return decoder;
    }

    @Bean
    public OAuth2TokenValidator<Jwt> jwtTokenValidator() {
        List<String> allowedIssuers = parseCsv(allowedIssuersCsv);
        List<OAuth2TokenValidator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        validators.add(allowedIssuersValidator(allowedIssuers));
        validators.add(tenantClaimValidator(requiredTenantId));
        validators.add(JwtValidators.createDefault());
        return new DelegatingOAuth2TokenValidator<>(validators);
    }

    private static OAuth2TokenValidator<Jwt> allowedIssuersValidator(List<String> allowed) {
        return jwt -> {
            String iss = jwt.getIssuer() != null ? jwt.getIssuer().toString() : null;
            if (iss == null || allowed.stream().noneMatch(iss::equals)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "invalid_token", "Issuer '" + iss + "' is not allowed", null));
            }
            return OAuth2TokenValidatorResult.success();
        };
    }

    // Package-private (not private) so the decode-time dual-accept can be
    // unit-tested directly without booting a NimbusJwtDecoder (TASK-MONO-162).
    static OAuth2TokenValidator<Jwt> tenantClaimValidator(String expectedTenantId) {
        Objects.requireNonNull(expectedTenantId, "expectedTenantId");
        return jwt -> {
            Object raw = jwt.getClaim("tenant_id");
            String tenantId = raw instanceof String s ? s : null;
            boolean legacyOk = tenantId != null && !tenantId.isBlank()
                    && ("*".equals(tenantId) || expectedTenantId.equals(tenantId));
            if (legacyOk) {
                return OAuth2TokenValidatorResult.success();
            }
            // Entitlement-trust dual-accept (ADR-MONO-019 § D5): the signed
            // entitled_domains claim may grant access even when tenant_id does
            // not match the legacy slug. Mirrors TenantClaimEnforcer.isEntitled
            // — both decode + filter layers must dual-accept (TASK-MONO-162).
            if (isEntitled(jwt, expectedTenantId)) {
                return OAuth2TokenValidatorResult.success();
            }
            if (tenantId == null || tenantId.isBlank()) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                        "tenant_mismatch", "tenant_id claim is required", null));
            }
            return OAuth2TokenValidatorResult.failure(new OAuth2Error(
                    "tenant_mismatch", "tenant_id '" + tenantId + "' is not allowed", null));
        };
    }

    /**
     * Decode-time entitlement-trust check. Returns {@code true} iff the verified
     * {@code entitled_domains} claim is a list of strings that contains
     * {@code domain}. Any claim shape anomaly (absent / non-list / null or
     * non-string element) yields {@code false} (fail-closed — no NPE, no blanket
     * trust). Mirrors {@code TenantClaimEnforcer.isEntitled} exactly (each
     * service owns its copy — the helper cannot be shared across modules).
     */
    static boolean isEntitled(Jwt jwt, String domain) {
        if (jwt == null || domain == null) {
            return false;
        }
        Object raw = jwt.getClaims().get(CLAIM_ENTITLED_DOMAINS);
        if (!(raw instanceof List<?> list)) {
            return false;
        }
        for (Object element : list) {
            if (element instanceof String s && s.equals(domain)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> parseCsv(String csv) {
        List<String> out = new ArrayList<>();
        if (csv == null) return out;
        for (String part : csv.split(",")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }
}
