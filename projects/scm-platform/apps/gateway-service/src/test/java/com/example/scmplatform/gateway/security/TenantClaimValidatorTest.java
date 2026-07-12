package com.example.scmplatform.gateway.security;

import com.example.security.oauth2.TenantClaimValidator;
import com.example.scmplatform.gateway.config.OAuth2ResourceServerConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantClaimValidator 단위 테스트 (scm-platform gateway)")
class TenantClaimValidatorTest {

    // Built from the production wiring, not hand-constructed: change the gate in
    // OAuth2ResourceServerConfig#tenantGate and these assertions go red (TASK-MONO-355).
    private final TenantClaimValidator validator = new OAuth2ResourceServerConfig(
            "http://iam.local/oauth2/jwks", "http://iam.local,iam", "scm").tenantGate();

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
    @DisplayName("tenant_id=scm → success")
    void scmTenantPasses() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID, "scm"));
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
    @DisplayName("tenant_id=fan-platform (cross-tenant) → tenant_mismatch")
    void crossTenantFanPlatformRejected() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID, "fan-platform"));
        assertThat(r.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("tenant_id=ecommerce (cross-tenant) → tenant_mismatch")
    void crossTenantEcommerceRejected() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID, "ecommerce"));
        assertThat(r.hasErrors()).isTrue();
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

    private static Jwt jwtWith(java.util.Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://iam.local")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(b::claim);
        return b.build();
    }

    @Test
    @DisplayName("entitlement-trust: tenant_id=acme + entitled_domains=[scm] → success")
    void entitledCrossTenantPasses() {
        OAuth2TokenValidatorResult r = validator.validate(jwtWith(java.util.Map.of(
                "tenant_id", "acme", "entitled_domains", java.util.List.of("scm"))));
        assertThat(r.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("non-entitled: tenant_id=acme + entitled_domains=[wms] → tenant_mismatch")
    void nonEntitledCrossTenantRejected() {
        OAuth2TokenValidatorResult r = validator.validate(jwtWith(java.util.Map.of(
                "tenant_id", "acme", "entitled_domains", java.util.List.of("wms"))));
        assertThat(r.hasErrors()).isTrue();
        assertThat(r.getErrors()).anyMatch(
                e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(e.getErrorCode()));
    }

    @Test
    @DisplayName("non-entitled: tenant_id=acme without entitled_domains → tenant_mismatch")
    void crossTenantNoEntitlementRejected() {
        OAuth2TokenValidatorResult r = validator.validate(jwtWith(java.util.Map.of(
                "tenant_id", "acme")));
        assertThat(r.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("legacy scm/* still pass with entitlement branch present")
    void legacyStillPasses() {
        assertThat(validator.validate(jwtWith(java.util.Map.of(
                "tenant_id", "scm", "entitled_domains", java.util.List.of("wms"))))
                .hasErrors()).isFalse();
        assertThat(validator.validate(jwtWith(java.util.Map.of("tenant_id", "*")))
                .hasErrors()).isFalse();
    }

    @Test
    @DisplayName("entitled_domains containing scm grants even when tenant_id absent")
    void entitledWithoutTenantIdPasses() {
        OAuth2TokenValidatorResult r = validator.validate(jwtWith(java.util.Map.of(
                "entitled_domains", java.util.List.of("scm"))));
        assertThat(r.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("claim shape safety: non-list / empty / non-string element → not entitled, fail-closed")
    void claimShapeSafety() {
        // non-list (String)
        assertThat(validator.validate(jwtWith(java.util.Map.of(
                "tenant_id", "acme", "entitled_domains", "scm"))).hasErrors()).isTrue();
        // empty list
        assertThat(validator.validate(jwtWith(java.util.Map.of(
                "tenant_id", "acme", "entitled_domains", java.util.List.of())))
                .hasErrors()).isTrue();
        // non-string element
        assertThat(validator.validate(jwtWith(java.util.Map.of(
                "tenant_id", "acme", "entitled_domains", java.util.List.of(42))))
                .hasErrors()).isTrue();
        // isEntitled static helper null-safety
        assertThat(TenantClaimValidator.isEntitled(null, "scm")).isFalse();
        assertThat(TenantClaimValidator.isEntitled(
                jwtWith(java.util.Map.of("tenant_id", "scm")), null)).isFalse();
        assertThat(TenantClaimValidator.isEntitled(
                jwtWith(java.util.Map.of("tenant_id", "acme")), "scm")).isFalse();
    }
}
