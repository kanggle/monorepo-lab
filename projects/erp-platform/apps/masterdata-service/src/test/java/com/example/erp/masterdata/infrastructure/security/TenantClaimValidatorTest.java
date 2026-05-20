package com.example.erp.masterdata.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TenantClaimValidatorTest {

    private static Jwt jwt(String tenantId) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .header("alg", "RS256")
                .subject("user-1");
        if (tenantId != null) {
            b.claim("tenant_id", tenantId);
        }
        b.claims(c -> c.putIfAbsent("scope", "erp.read"));
        return b.build();
    }

    @Test
    @DisplayName("missing tenant_id → failure")
    void missingTenant() {
        OAuth2TokenValidatorResult r = new TenantClaimValidator("erp").validate(jwt(null));
        assertThat(r.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("matching tenant_id → success")
    void matchingTenant() {
        OAuth2TokenValidatorResult r = new TenantClaimValidator("erp").validate(jwt("erp"));
        assertThat(r.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("wildcard tenant_id ('*') → success")
    void wildcardTenant() {
        OAuth2TokenValidatorResult r = new TenantClaimValidator("erp").validate(jwt("*"));
        assertThat(r.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("cross-tenant tenant_id → failure (tenant_mismatch)")
    void crossTenant() {
        OAuth2TokenValidatorResult r = new TenantClaimValidator("erp").validate(jwt("scm"));
        assertThat(r.hasErrors()).isTrue();
        assertThat(r.getErrors()).anyMatch(e ->
                TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(e.getErrorCode()));
    }
}
