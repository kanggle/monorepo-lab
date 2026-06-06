package com.example.finance.account.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TenantClaimValidator} + {@link AllowedIssuersValidator}
 * (architecture.md § Multi-tenancy — fail-closed cross-tenant rejection).
 */
class TenantClaimValidatorTest {

    private Jwt jwtWith(Map<String, Object> claims) {
        return new Jwt("token", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), claims);
    }

    @Test
    @DisplayName("tenant_id=finance passes")
    void financePasses() {
        var v = new TenantClaimValidator("finance");
        assertThat(v.validate(jwtWith(Map.of("tenant_id", "finance",
                "sub", "s"))).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("tenant_id=* (platform scope) passes")
    void wildcardPasses() {
        var v = new TenantClaimValidator("finance");
        assertThat(v.validate(jwtWith(Map.of("tenant_id", "*",
                "sub", "s"))).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("cross-tenant (tenant_id=wms) fails closed")
    void crossTenantFails() {
        var v = new TenantClaimValidator("finance");
        var result = v.validate(jwtWith(Map.of("tenant_id", "wms", "sub", "s")));
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors().iterator().next().getErrorCode())
                .isEqualTo(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH);
    }

    @Test
    @DisplayName("missing tenant_id fails closed")
    void missingFails() {
        var v = new TenantClaimValidator("finance");
        assertThat(v.validate(jwtWith(Map.of("sub", "s"))).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("entitlement-trust: tenant_id=acme + entitled_domains=[finance] passes")
    void entitledCrossTenantPasses() {
        var v = new TenantClaimValidator("finance");
        var result = v.validate(jwtWith(Map.of("tenant_id", "acme",
                "entitled_domains", java.util.List.of("finance"), "sub", "s")));
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("non-entitled: tenant_id=acme + entitled_domains=[wms] fails closed")
    void nonEntitledCrossTenantFails() {
        var v = new TenantClaimValidator("finance");
        var result = v.validate(jwtWith(Map.of("tenant_id", "acme",
                "entitled_domains", java.util.List.of("wms"), "sub", "s")));
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors().iterator().next().getErrorCode())
                .isEqualTo(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH);
    }

    @Test
    @DisplayName("non-entitled: tenant_id=acme without entitled_domains fails closed")
    void crossTenantNoEntitlementFails() {
        var v = new TenantClaimValidator("finance");
        assertThat(v.validate(jwtWith(Map.of("tenant_id", "acme", "sub", "s")))
                .hasErrors()).isTrue();
    }

    @Test
    @DisplayName("legacy finance/* still pass with entitlement branch present")
    void legacyStillPasses() {
        var v = new TenantClaimValidator("finance");
        assertThat(v.validate(jwtWith(Map.of("tenant_id", "finance",
                "entitled_domains", java.util.List.of("wms"), "sub", "s")))
                .hasErrors()).isFalse();
        assertThat(v.validate(jwtWith(Map.of("tenant_id", "*", "sub", "s")))
                .hasErrors()).isFalse();
    }

    @Test
    @DisplayName("entitled_domains containing finance grants even when tenant_id absent")
    void entitledWithoutTenantIdPasses() {
        var v = new TenantClaimValidator("finance");
        assertThat(v.validate(jwtWith(Map.of(
                "entitled_domains", java.util.List.of("finance"), "sub", "s")))
                .hasErrors()).isFalse();
    }

    @Test
    @DisplayName("claim shape safety: non-list / empty / non-string element → not entitled, fail-closed")
    void claimShapeSafety() {
        var v = new TenantClaimValidator("finance");
        // non-list (String)
        assertThat(v.validate(jwtWith(Map.of("tenant_id", "acme",
                "entitled_domains", "finance", "sub", "s"))).hasErrors()).isTrue();
        // empty list
        assertThat(v.validate(jwtWith(Map.of("tenant_id", "acme",
                "entitled_domains", java.util.List.of(), "sub", "s")))
                .hasErrors()).isTrue();
        // non-string element (the matching domain is an Integer, not "finance")
        assertThat(v.validate(jwtWith(Map.of("tenant_id", "acme",
                "entitled_domains", java.util.List.of(42), "sub", "s")))
                .hasErrors()).isTrue();
        // isEntitled static helper null-safety
        assertThat(TenantClaimValidator.isEntitled(null, "finance")).isFalse();
        assertThat(TenantClaimValidator.isEntitled(
                jwtWith(Map.of("sub", "s")), null)).isFalse();
        assertThat(TenantClaimValidator.isEntitled(
                jwtWith(Map.of("sub", "s")), "finance")).isFalse();
    }

    @Test
    @DisplayName("AllowedIssuersValidator accepts listed issuer, rejects others")
    void issuerValidator() {
        var v = new AllowedIssuersValidator(
                java.util.List.of("http://iam.local", "iam"));
        assertThat(v.validate(jwtWith(Map.of("iss", "http://iam.local",
                "sub", "s"))).hasErrors()).isFalse();
        assertThat(v.validate(jwtWith(Map.of("iss", "http://evil",
                "sub", "s"))).hasErrors()).isTrue();
    }
}
