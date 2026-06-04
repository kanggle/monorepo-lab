package com.example.erp.readmodel.config.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the decode-time {@link TenantClaimValidator} — entitlement-trust
 * dual-accept gate (ADR-MONO-019 § D5). Accept on legacy tenant_id OR signed
 * entitled_domains; reject only when both fail (fail-closed).
 */
class TenantClaimValidatorTest {

    private final TenantClaimValidator validator = new TenantClaimValidator("erp");

    private static Jwt jwt(Map<String, Object> claims) {
        return new Jwt("t", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), claims);
    }

    @Test
    void erpTenantPasses() {
        OAuth2TokenValidatorResult r = validator.validate(jwt(Map.of("tenant_id", "erp", "sub", "u")));
        assertThat(r.hasErrors()).isFalse();
    }

    @Test
    void wildcardSuperAdminPasses() {
        assertThat(validator.validate(jwt(Map.of("tenant_id", "*", "sub", "u"))).hasErrors())
                .isFalse();
    }

    @Test
    void crossTenantWithoutEntitlementFails() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwt(Map.of("tenant_id", "scm", "sub", "u")));
        assertThat(r.hasErrors()).isTrue();
        assertThat(r.getErrors().iterator().next().getErrorCode())
                .isEqualTo(TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH);
    }

    @Test
    void entitledCrossTenantPasses() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwt(Map.of("tenant_id", "acme", "entitled_domains", List.of("erp"), "sub", "u")));
        assertThat(r.hasErrors()).isFalse();
    }

    @Test
    void nonEntitledCrossTenantFails() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwt(Map.of("tenant_id", "acme", "entitled_domains", List.of("scm"), "sub", "u")));
        assertThat(r.hasErrors()).isTrue();
    }

    @Test
    void absentTenantIdFails() {
        OAuth2TokenValidatorResult r = validator.validate(jwt(Map.of("sub", "u")));
        assertThat(r.hasErrors()).isTrue();
    }

    @Test
    void isEntitledClaimShapeSafety() {
        Jwt noClaim = jwt(Map.of("tenant_id", "acme", "sub", "u"));
        Jwt nonList = jwt(Map.of("tenant_id", "acme", "entitled_domains", "erp", "sub", "u"));
        Jwt nonString = jwt(Map.of("tenant_id", "acme", "entitled_domains", List.of(42), "sub", "u"));
        assertThat(TenantClaimValidator.isEntitled(null, "erp")).isFalse();
        assertThat(TenantClaimValidator.isEntitled(noClaim, null)).isFalse();
        assertThat(TenantClaimValidator.isEntitled(noClaim, "erp")).isFalse();
        assertThat(TenantClaimValidator.isEntitled(nonList, "erp")).isFalse();
        assertThat(TenantClaimValidator.isEntitled(nonString, "erp")).isFalse();
    }
}
