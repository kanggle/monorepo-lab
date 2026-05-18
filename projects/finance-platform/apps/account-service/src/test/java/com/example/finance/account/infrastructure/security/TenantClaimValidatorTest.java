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
    @DisplayName("AllowedIssuersValidator accepts listed issuer, rejects others")
    void issuerValidator() {
        var v = new AllowedIssuersValidator(
                java.util.List.of("http://gap.local", "global-account-platform"));
        assertThat(v.validate(jwtWith(Map.of("iss", "http://gap.local",
                "sub", "s"))).hasErrors()).isFalse();
        assertThat(v.validate(jwtWith(Map.of("iss", "http://evil",
                "sub", "s"))).hasErrors()).isTrue();
    }
}
