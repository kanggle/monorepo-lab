package com.example.fanplatform.gateway.security;

import com.example.security.oauth2.TenantClaimValidator;
import com.example.fanplatform.gateway.config.OAuth2ResourceServerConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantClaimValidator 단위 테스트 (fan-platform gateway)")
class TenantClaimValidatorTest {

    // Built from the production wiring, not hand-constructed: change the gate in
    // OAuth2ResourceServerConfig#tenantGate and these assertions go red (TASK-MONO-355).
    private final TenantClaimValidator validator = new OAuth2ResourceServerConfig(
            "http://iam.local/.well-known/jwks.json", "http://iam.local,iam", "fan-platform").tenantGate();

    private static Jwt jwtWithClaim(String name, Object value) {
        return Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://iam.local")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim(name, value)
                .build();
    }

    @Test
    @DisplayName("tenant_id=fan-platform → success")
    void fanPlatformTenantPasses() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID, "fan-platform"));
        assertThat(r.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("tenant_id=wms (cross-tenant) → tenant_mismatch")
    void crossTenantWmsRejected() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID, "wms"));
        assertThat(r.hasErrors()).isTrue();
        assertThat(r.getErrors()).anyMatch(
                e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(e.getErrorCode()));
    }

    @Test
    @DisplayName("tenant_id=ecommerce (cross-tenant) → tenant_mismatch")
    void crossTenantEcommerceRejected() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID, "ecommerce"));
        assertThat(r.hasErrors()).isTrue();
    }

    /**
     * fan has <strong>no entitlement branch</strong>, and that is correct rather than an
     * omission (ADR-MONO-048 § D5): fan is absent from {@code ProductCatalog.ENTRIES},
     * {@code V0019} seeds subscriptions only for wms/scm/erp/finance, and {@code fan-platform}
     * is a {@code B2C_CONSUMER} tenant. It sits outside the entitlement plane entirely.
     *
     * <p>TASK-MONO-355 found nothing was asserting that. Adding {@code .trustEntitledDomains()}
     * to {@code OAuth2ResourceServerConfig#tenantGate} "for symmetry" would not merely add dead
     * code to a production security filter — it would make an entitled cross-tenant token pass
     * fan's edge, and the suite would have stayed green. This test is the guard.
     */
    @Test
    @DisplayName("entitled_domains=[fan-platform] 이어도 교차 테넌트는 거부 — fan 은 엔타이틀먼트 평면 밖이다")
    void entitledDomainsIsNotConsulted() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://iam.local")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .claim(TenantClaimValidator.CLAIM_TENANT_ID, "wms")
                .claim(TenantClaimValidator.CLAIM_ENTITLED_DOMAINS, java.util.List.of("fan-platform"))
                .build();

        OAuth2TokenValidatorResult r = validator.validate(jwt);

        assertThat(r.hasErrors()).isTrue();
        assertThat(r.getErrors()).anyMatch(
                e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(e.getErrorCode()));
    }

    @Test
    @DisplayName("tenant_id=* (SUPER_ADMIN platform-scope) → success")
    void wildcardTenantPasses() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID,
                        TenantClaimValidator.WILDCARD_TENANT));
        assertThat(r.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("tenant_id 미존재 → tenant_mismatch (claim is required)")
    void missingTenantRejected() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://iam.local")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        OAuth2TokenValidatorResult r = validator.validate(jwt);
        assertThat(r.hasErrors()).isTrue();
        assertThat(r.getErrors()).anyMatch(
                e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(e.getErrorCode()));
    }

    @Test
    @DisplayName("tenant_id=blank → tenant_mismatch")
    void blankTenantRejected() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID, "   "));
        assertThat(r.hasErrors()).isTrue();
    }
}
