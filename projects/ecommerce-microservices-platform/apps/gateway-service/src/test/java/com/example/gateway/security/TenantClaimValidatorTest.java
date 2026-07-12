package com.example.gateway.security;

import com.example.security.oauth2.TenantClaimValidator;
import com.example.gateway.config.OAuth2ResourceServerConfig;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantClaimValidator 단위 테스트 (gateway)")
class TenantClaimValidatorTest {

    // Built from the production wiring, not hand-constructed: change the gate in
    // OAuth2ResourceServerConfig#tenantGate and these assertions go red (TASK-MONO-356).
    private final TenantClaimValidator validator = new OAuth2ResourceServerConfig(
            "http://iam.local/oauth2/jwks", "http://iam.local,iam", "ecommerce").tenantGate();

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
    @DisplayName("tenant_id=ecommerce (legacy slug, dual-accept) → success")
    void ecommerceTenantPasses() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID, "ecommerce"));
        assertThat(r.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("tenant_id=globex (arbitrary entitled tenant) → success (entitlement-trust)")
    void arbitraryTenantPasses() {
        // ADR-MONO-030 § 2.4: the multi-tenant SaaS edge accepts any well-formed
        // tenant_id from a verified token; entitlement is decided at IAM issuance.
        OAuth2TokenValidatorResult r = validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID, "globex"));
        assertThat(r.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("tenant_id=wms (formerly cross-tenant) → success under entitlement-trust")
    void formerlyCrossTenantWmsNowPasses() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID, "wms"));
        assertThat(r.hasErrors()).isFalse();
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
