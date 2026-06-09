package com.example.fanplatform.membership.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TenantClaimValidatorTest {

    private final TenantClaimValidator validator = new TenantClaimValidator("fan-platform");

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .subject("acc1");
        claims.forEach(b::claim);
        return b.build();
    }

    @Test
    @DisplayName("matching tenant_id passes")
    void matchingTenant() {
        assertThat(validator.validate(jwt(Map.of("tenant_id", "fan-platform"))).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("wildcard tenant passes")
    void wildcardTenant() {
        assertThat(validator.validate(jwt(Map.of("tenant_id", "*"))).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("cross-tenant fails with tenant_mismatch")
    void crossTenant() {
        var result = validator.validate(jwt(Map.of("tenant_id", "wms")));
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).anyMatch(
                e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(e.getErrorCode()));
    }

    @Test
    @DisplayName("missing tenant_id fails")
    void missingTenant() {
        assertThat(validator.validate(jwt(Map.of("foo", "bar"))).hasErrors()).isTrue();
    }
}
