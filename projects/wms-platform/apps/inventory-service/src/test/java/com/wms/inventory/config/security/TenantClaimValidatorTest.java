package com.wms.inventory.config.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TenantClaimValidator 단위 테스트 (inventory)")
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
    void wmsTenantPasses() {
        assertThat(validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID, "wms"))
                .hasErrors()).isFalse();
    }

    @Test
    void crossTenantRejected() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID, "fan-platform"));
        assertThat(r.hasErrors()).isTrue();
        assertThat(r.getErrors()).anyMatch(
                e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(e.getErrorCode()));
    }

    @Test
    void missingTenantRejected() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://localhost:8081")
                .subject("operator-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    @Test
    void blankTenantRejected() {
        assertThat(validator.validate(
                jwtWithClaim(TenantClaimValidator.CLAIM_TENANT_ID, "  "))
                .hasErrors()).isTrue();
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
    void entitledDomainGrantsAccessDespiteCrossTenant() {
        assertThat(validator.validate(jwtWith("fan-platform", List.of("wms")))
                .hasErrors()).isFalse();
    }

    @Test
    void entitledDomainForOtherDomainDoesNotHelp() {
        OAuth2TokenValidatorResult r = validator.validate(
                jwtWith("fan-platform", List.of("scm")));
        assertThat(r.hasErrors()).isTrue();
        assertThat(r.getErrors()).anyMatch(
                e -> TenantClaimValidator.ERROR_CODE_TENANT_MISMATCH.equals(e.getErrorCode()));
    }

    @Test
    void malformedEntitledDomainsFailsClosed() {
        assertThat(validator.validate(jwtWith(null, List.of(123)))
                .hasErrors()).isTrue();
    }
}
