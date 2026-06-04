package com.example.erp.approval.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TenantClaimValidatorTest {

    private final TenantClaimValidator validator = new TenantClaimValidator("erp");

    private Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(b::claim);
        return b.build();
    }

    @Test
    @DisplayName("tenant_id=erp → success")
    void tenantErpOk() {
        assertThat(validator.validate(jwt(Map.of("tenant_id", "erp"))).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("tenant_id=* (SUPER_ADMIN) → success")
    void wildcardOk() {
        assertThat(validator.validate(jwt(Map.of("tenant_id", "*"))).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("cross-tenant + no entitlement → failure")
    void crossTenantFails() {
        assertThat(validator.validate(jwt(Map.of("tenant_id", "wms"))).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("entitlement-trust dual-accept: entitled_domains ∋ erp → success even if tenant mismatch")
    void entitlementDualAccept() {
        assertThat(validator.validate(jwt(Map.of(
                "tenant_id", "wms",
                "entitled_domains", List.of("erp", "finance")))).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("missing tenant_id + no entitlement → failure")
    void missingTenant() {
        assertThat(validator.validate(jwt(Map.of("sub", "x"))).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("isEntitled fail-closed on malformed claim")
    void isEntitledFailClosed() {
        assertThat(TenantClaimValidator.isEntitled(jwt(Map.of("entitled_domains", "erp")), "erp"))
                .isFalse(); // string, not list → not entitled
        assertThat(TenantClaimValidator.isEntitled(jwt(Map.of("sub", "x")), "erp")).isFalse();
    }
}
