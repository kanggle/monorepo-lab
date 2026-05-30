package com.example.scmplatform.procurement.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TenantClaimValidator} — entitlement-trust dual-accept
 * fail-closed cross-tenant rejection (ADR-MONO-019 § D5).
 */
class TenantClaimValidatorTest {

    private final TenantClaimValidator validator = new TenantClaimValidator("scm");

    private static Jwt jwtWith(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), claims);
    }

    @Test
    @DisplayName("tenant_id=scm → success")
    void scmPasses() {
        assertThat(validator.validate(jwtWith(Map.of("tenant_id", "scm", "sub", "s")))
                .hasErrors()).isFalse();
    }

    @Test
    @DisplayName("tenant_id=* (SUPER_ADMIN platform-scope) → success")
    void wildcardPasses() {
        assertThat(validator.validate(jwtWith(Map.of("tenant_id", "*", "sub", "s")))
                .hasErrors()).isFalse();
    }

    @Test
    @DisplayName("cross-tenant (tenant_id=wms) → tenant_mismatch")
    void crossTenantFails() {
        var result = validator.validate(jwtWith(Map.of("tenant_id", "wms", "sub", "s")));
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors().iterator().next().getErrorCode())
                .isEqualTo(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH);
    }

    @Test
    @DisplayName("missing tenant_id → tenant_mismatch")
    void missingFails() {
        assertThat(validator.validate(jwtWith(Map.of("sub", "s"))).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("entitlement-trust: tenant_id=acme + entitled_domains=[scm] → success")
    void entitledCrossTenantPasses() {
        var result = validator.validate(jwtWith(Map.of("tenant_id", "acme",
                "entitled_domains", List.of("scm"), "sub", "s")));
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("non-entitled: tenant_id=acme + entitled_domains=[wms] → tenant_mismatch")
    void nonEntitledCrossTenantFails() {
        var result = validator.validate(jwtWith(Map.of("tenant_id", "acme",
                "entitled_domains", List.of("wms"), "sub", "s")));
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors().iterator().next().getErrorCode())
                .isEqualTo(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH);
    }

    @Test
    @DisplayName("non-entitled: tenant_id=acme without entitled_domains → tenant_mismatch")
    void crossTenantNoEntitlementFails() {
        assertThat(validator.validate(jwtWith(Map.of("tenant_id", "acme", "sub", "s")))
                .hasErrors()).isTrue();
    }

    @Test
    @DisplayName("legacy scm/* still pass with entitlement branch present")
    void legacyStillPasses() {
        assertThat(validator.validate(jwtWith(Map.of("tenant_id", "scm",
                "entitled_domains", List.of("wms"), "sub", "s"))).hasErrors()).isFalse();
        assertThat(validator.validate(jwtWith(Map.of("tenant_id", "*", "sub", "s")))
                .hasErrors()).isFalse();
    }

    @Test
    @DisplayName("entitled_domains containing scm grants even when tenant_id absent")
    void entitledWithoutTenantIdPasses() {
        assertThat(validator.validate(jwtWith(Map.of(
                "entitled_domains", List.of("scm"), "sub", "s"))).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("claim shape safety: non-list / empty / non-string element → not entitled, fail-closed")
    void claimShapeSafety() {
        // non-list (String)
        assertThat(validator.validate(jwtWith(Map.of("tenant_id", "acme",
                "entitled_domains", "scm", "sub", "s"))).hasErrors()).isTrue();
        // empty list
        assertThat(validator.validate(jwtWith(Map.of("tenant_id", "acme",
                "entitled_domains", List.of(), "sub", "s"))).hasErrors()).isTrue();
        // non-string element
        assertThat(validator.validate(jwtWith(Map.of("tenant_id", "acme",
                "entitled_domains", List.of(42), "sub", "s"))).hasErrors()).isTrue();
        // isEntitled static helper null-safety
        assertThat(TenantClaimValidator.isEntitled(null, "scm")).isFalse();
        assertThat(TenantClaimValidator.isEntitled(
                jwtWith(Map.of("sub", "s")), null)).isFalse();
        assertThat(TenantClaimValidator.isEntitled(
                jwtWith(Map.of("sub", "s")), "scm")).isFalse();
    }
}
