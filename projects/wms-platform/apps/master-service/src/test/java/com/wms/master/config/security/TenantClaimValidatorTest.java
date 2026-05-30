package com.wms.master.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

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

    private static Jwt jwtWith(Object tenantId, Object entitledDomains) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://localhost:8081")
                .subject("operator-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        if (tenantId != null) {
            b.claim(TenantClaimValidator.CLAIM_TENANT_ID, tenantId);
        }
        if (entitledDomains != null) {
            b.claim(TenantClaimValidator.CLAIM_ENTITLED_DOMAINS, entitledDomains);
        }
        return b.build();
    }

    @Test
    @DisplayName("entitled_domains=[wms] + tenant_id=fan-platform → success (entitlement grants access)")
    void entitledDomainGrantsAccessDespiteCrossTenant() {
        OAuth2TokenValidatorResult result = validator.validate(
                jwtWith("fan-platform", List.of("wms")));
        assertThat(result.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("entitled_domains=[scm] + tenant_id=fan-platform → tenant_mismatch error")
    void entitledDomainForOtherDomainDoesNotHelp() {
        OAuth2TokenValidatorResult result = validator.validate(
                jwtWith("fan-platform", List.of("scm")));
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors())
                .anyMatch(e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(e.getErrorCode()));
    }

    @Test
    @DisplayName("entitled_domains malformed (non-string element) + no tenant_id → fail-closed")
    void malformedEntitledDomainsFailsClosed() {
        OAuth2TokenValidatorResult result = validator.validate(
                jwtWith(null, List.of(123)));
        assertThat(result.hasErrors()).isTrue();
    }
}
