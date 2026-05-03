package com.example.gateway.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantClaimValidator 단위 테스트 (gateway)")
class TenantClaimValidatorTest {

    private final TenantClaimValidator validator = new TenantClaimValidator("ecommerce");

    private static Jwt jwtWithClaim(String name, Object value) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://localhost:8081")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim(name, value)
                .build();
    }

    @Test
    @DisplayName("tenant_id=ecommerce → success")
    void ecommerceTenantPasses() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID, "ecommerce"));
        assertThat(r.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("tenant_id=wms → tenant_mismatch")
    void crossTenantWmsRejected() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID, "wms"));
        assertThat(r.hasErrors()).isTrue();
        assertThat(r.getErrors()).anyMatch(
                e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(e.getErrorCode()));
    }

    @Test
    @DisplayName("tenant_id=fan-platform → tenant_mismatch")
    void crossTenantFanPlatformRejected() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID, "fan-platform"));
        assertThat(r.hasErrors()).isTrue();
        assertThat(r.getErrors()).anyMatch(
                e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(e.getErrorCode()));
    }

    @Test
    @DisplayName("tenant_id 미존재 → tenant_mismatch")
    void missingTenantRejected() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://localhost:8081")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        OAuth2TokenValidatorResult r = validator.validate(jwt);
        assertThat(r.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("tenant_id=blank → tenant_mismatch")
    void blankTenantRejected() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID, "   "));
        assertThat(r.hasErrors()).isTrue();
    }
}
