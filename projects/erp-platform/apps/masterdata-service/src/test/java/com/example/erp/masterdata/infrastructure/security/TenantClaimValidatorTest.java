package com.example.erp.masterdata.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TenantClaimValidatorTest {

    private static Jwt jwt(String tenantId) {
        return jwt(tenantId, null);
    }

    private static Jwt jwt(String tenantId, Object entitledDomains) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .header("alg", "RS256")
                .subject("user-1");
        if (tenantId != null) {
            b.claim("tenant_id", tenantId);
        }
        if (entitledDomains != null) {
            b.claim("entitled_domains", entitledDomains);
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

    @Test
    @DisplayName("entitlement-trust: tenant_id=wms + entitled_domains=[erp] → success")
    void entitledCrossTenantPasses() {
        OAuth2TokenValidatorResult r = new TenantClaimValidator("erp")
                .validate(jwt("wms", List.of("erp")));
        assertThat(r.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("non-entitled: tenant_id=wms + entitled_domains=[scm] → failure")
    void nonEntitledCrossTenantFails() {
        OAuth2TokenValidatorResult r = new TenantClaimValidator("erp")
                .validate(jwt("wms", List.of("scm")));
        assertThat(r.hasErrors()).isTrue();
        assertThat(r.getErrors()).anyMatch(e ->
                TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(e.getErrorCode()));
    }

    @Test
    @DisplayName("non-entitled: tenant_id=wms without entitled_domains → failure")
    void crossTenantNoEntitlementFails() {
        OAuth2TokenValidatorResult r = new TenantClaimValidator("erp").validate(jwt("wms"));
        assertThat(r.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("legacy erp/* still pass with entitlement branch present")
    void legacyStillPasses() {
        assertThat(new TenantClaimValidator("erp")
                .validate(jwt("erp", List.of("scm"))).hasErrors()).isFalse();
        assertThat(new TenantClaimValidator("erp")
                .validate(jwt("*", List.of("scm"))).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("entitled_domains containing erp grants even when tenant_id absent")
    void entitledWithoutTenantIdPasses() {
        OAuth2TokenValidatorResult r = new TenantClaimValidator("erp")
                .validate(jwt(null, List.of("erp")));
        assertThat(r.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("claim shape safety: non-list / empty / non-string element → not entitled, fail-closed")
    void claimShapeSafety() {
        var v = new TenantClaimValidator("erp");
        // non-list (String)
        assertThat(v.validate(jwt("wms", "erp")).hasErrors()).isTrue();
        // empty list
        assertThat(v.validate(jwt("wms", List.of())).hasErrors()).isTrue();
        // non-string element (the matching domain is an Integer, not "erp")
        assertThat(v.validate(jwt("wms", List.of(42))).hasErrors()).isTrue();
        // isEntitled static helper null-safety
        assertThat(TenantClaimValidator.isEntitled(null, "erp")).isFalse();
        assertThat(TenantClaimValidator.isEntitled(jwt("wms"), null)).isFalse();
        assertThat(TenantClaimValidator.isEntitled(jwt("wms"), "erp")).isFalse();
    }
}
