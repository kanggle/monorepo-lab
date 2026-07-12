package com.wms.gateway.security;

import com.example.security.oauth2.TenantClaimValidator;
import com.wms.gateway.config.OAuth2ResourceServerConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantClaimValidator 단위 테스트 (gateway)")
class TenantClaimValidatorTest {

    // Built from the production wiring, not hand-constructed: relax the gate in
    // OAuth2ResourceServerConfig#tenantGate and these assertions go red (TASK-MONO-355).
    private final TenantClaimValidator validator = new OAuth2ResourceServerConfig(
            "http://iam.local/oauth2/jwks", "http://iam.local,iam", "wms").tenantGate();

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

    /**
     * wms rejects the {@code "*"} SUPER_ADMIN wildcard that scm and fan accept. ADR-MONO-048
     * § D5 confirmed this is a documented choice rather than drift — and then TASK-MONO-355
     * found that <strong>nothing was asserting it</strong>. The suite pinned what this gate
     * <em>accepts</em> and never what it deliberately refuses, so adding
     * {@code .allowSuperAdminWildcard()} to {@code OAuth2ResourceServerConfig#tenantGate}
     * would have opened the wms edge to every platform-scope token in complete silence.
     *
     * <p>The validator under test is built from that method, so this test is the guard.
     */
    @Test
    @DisplayName("tenant_id=* (SUPER_ADMIN wildcard) → tenant_mismatch — wms 만의 의도적 거부")
    void superAdminWildcardRejected() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID,
                        TenantClaimValidator.WILDCARD_TENANT));
        assertThat(r.hasErrors()).isTrue();
        assertThat(r.getErrors()).anyMatch(
                e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(e.getErrorCode()));
    }

    @Test
    @DisplayName("tenant_id=wms → success")
    void wmsTenantPasses() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID, "wms"));
        assertThat(r.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("tenant_id=fan-platform → tenant_mismatch")
    void crossTenantRejected() {
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

    private static Jwt jwtWith(Object tenantId, Object entitledDomains) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://localhost:8081")
                .subject("user-1")
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
        OAuth2TokenValidatorResult r = validator.validate(
                jwtWith("fan-platform", List.of("wms")));
        assertThat(r.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("entitled_domains=[scm] + tenant_id=fan-platform → tenant_mismatch")
    void entitledDomainForOtherDomainDoesNotHelp() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwtWith("fan-platform", List.of("scm")));
        assertThat(r.hasErrors()).isTrue();
        assertThat(r.getErrors()).anyMatch(
                e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(e.getErrorCode()));
    }

    @Test
    @DisplayName("entitled_domains malformed (non-string element) + no tenant_id → fail-closed")
    void malformedEntitledDomainsFailsClosed() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwtWith(null, List.of(123)));
        assertThat(r.hasErrors()).isTrue();
    }
}
