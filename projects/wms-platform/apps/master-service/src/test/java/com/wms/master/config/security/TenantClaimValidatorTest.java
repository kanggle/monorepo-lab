package com.wms.master.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for TASK-MONO-019's tenant_id claim validator.
 */
@DisplayName("TenantClaimValidator 단위 테스트")
class TenantClaimValidatorTest {

    private final TenantClaimValidator validator = new TenantClaimValidator("wms");

    private static Jwt jwtWithClaim(String name, Object value) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://localhost:8081")
                .subject("operator-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim(name, value)
                .build();
    }

    @Test
    @DisplayName("tenant_id=wms → success")
    void wmsTenantPasses() {
        OAuth2TokenValidatorResult result = validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID, "wms"));
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("tenant_id=fan-platform → tenant_mismatch error")
    void crossTenantFanPlatformRejected() {
        OAuth2TokenValidatorResult result = validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID, "fan-platform"));
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .anyMatch(e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(e.getErrorCode()));
    }

    @Test
    @DisplayName("tenant_id 미존재 → tenant_mismatch error")
    void missingTenantRejected() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://localhost:8081")
                .subject("operator-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .anyMatch(e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(e.getErrorCode()));
    }

    @Test
    @DisplayName("tenant_id=blank → tenant_mismatch error")
    void blankTenantRejected() {
        OAuth2TokenValidatorResult result = validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID, "  "));
        assertThat(result.hasErrors()).isTrue();
    }
}
