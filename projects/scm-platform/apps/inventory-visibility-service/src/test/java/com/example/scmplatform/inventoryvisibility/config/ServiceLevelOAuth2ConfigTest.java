package com.example.scmplatform.inventoryvisibility.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the decode-time {@code tenantClaimValidator} entitlement-trust
 * dual-accept (ADR-MONO-019 § D5, TASK-MONO-162). Layer 1 (decode validator)
 * must dual-accept exactly like Layer 2 ({@code TenantClaimEnforcer}) — a
 * domain-entitled cross-tenant token (e.g. globex {@code entitled_domains=[scm]})
 * must DECODE so the snapshot READ then passes the filter; a token with neither
 * legacy slug nor entitlement is rejected (fail-closed).
 */
class ServiceLevelOAuth2ConfigTest {

    private final OAuth2TokenValidator<Jwt> validator =
            ServiceLevelOAuth2Config.tenantClaimValidator("scm");

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt("t", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), claims);
    }

    @Test
    @DisplayName("legacy: tenant_id=scm decodes")
    void legacyScmDecodes() {
        assertThat(validator.validate(jwt(Map.of("tenant_id", "scm", "sub", "u"))).hasErrors())
                .isFalse();
    }

    @Test
    @DisplayName("wildcard: tenant_id=* (SUPER_ADMIN) decodes")
    void wildcardDecodes() {
        assertThat(validator.validate(jwt(Map.of("tenant_id", "*", "sub", "u"))).hasErrors())
                .isFalse();
    }

    @Test
    @DisplayName("entitlement-trust: tenant_id=globex-corp + entitled_domains=[scm,erp] decodes")
    void entitledCrossTenantDecodes() {
        Jwt globex = jwt(Map.of(
                "tenant_id", "globex-corp",
                "entitled_domains", List.of("scm", "erp"),
                "sub", "u"));
        OAuth2TokenValidatorResult result = validator.validate(globex);
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("entitlement-trust grants even when tenant_id absent")
    void entitledWithoutTenantIdDecodes() {
        assertThat(validator.validate(jwt(Map.of("entitled_domains", List.of("scm"), "sub", "u")))
                .hasErrors()).isFalse();
    }

    @Test
    @DisplayName("fail-closed: tenant_id=acme + entitled_domains=[wms] (no scm) → rejected")
    void nonEntitledCrossTenantRejected() {
        Jwt acme = jwt(Map.of(
                "tenant_id", "acme",
                "entitled_domains", List.of("wms"),
                "sub", "u"));
        OAuth2TokenValidatorResult result = validator.validate(acme);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> "tenant_mismatch".equals(e.getErrorCode()));
    }

    @Test
    @DisplayName("fail-closed: neither tenant_id nor entitled_domains → rejected")
    void noTenantNoEntitlementRejected() {
        OAuth2TokenValidatorResult result = validator.validate(jwt(Map.of("sub", "u")));
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(e -> "tenant_mismatch".equals(e.getErrorCode()));
    }

    @Test
    @DisplayName("isEntitled claim-shape safety: null / non-list / non-string element → false")
    void isEntitledClaimShapeSafety() {
        Jwt acmeNoClaim = jwt(Map.of("tenant_id", "acme", "sub", "u"));
        Jwt nonList = jwt(Map.of("tenant_id", "acme", "entitled_domains", "scm", "sub", "u"));
        Jwt nonStringElement = jwt(Map.of("tenant_id", "acme",
                "entitled_domains", List.of(42), "sub", "u"));
        assertThat(ServiceLevelOAuth2Config.isEntitled(null, "scm")).isFalse();
        assertThat(ServiceLevelOAuth2Config.isEntitled(acmeNoClaim, null)).isFalse();
        assertThat(ServiceLevelOAuth2Config.isEntitled(acmeNoClaim, "scm")).isFalse();
        assertThat(ServiceLevelOAuth2Config.isEntitled(nonList, "scm")).isFalse();
        assertThat(ServiceLevelOAuth2Config.isEntitled(nonStringElement, "scm")).isFalse();
    }
}
