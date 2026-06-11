package com.example.scmplatform.demandplanning.integration;

import com.example.scmplatform.demandplanning.config.ServiceLevelOAuth2Config;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tenant fail-closed unit test (no Spring context needed).
 * AC-5: tenant_id=wms → rejected; tenant_id=scm → accepted.
 */
class TenantFailClosedIntegrationTest {

    @Test
    void scmTenantId_isAccepted() {
        var validator = ServiceLevelOAuth2Config.tenantClaimValidator("scm");
        Jwt jwt = buildJwt(Map.of("tenant_id", "scm"));
        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void wmsTenantId_isRejected_AC5() {
        var validator = ServiceLevelOAuth2Config.tenantClaimValidator("scm");
        Jwt jwt = buildJwt(Map.of("tenant_id", "wms"));
        OAuth2TokenValidatorResult result = validator.validate(jwt);
        assertThat(result.hasErrors()).isTrue();
    }

    @Test
    void wildcardTenantId_isAccepted() {
        var validator = ServiceLevelOAuth2Config.tenantClaimValidator("scm");
        Jwt jwt = buildJwt(Map.of("tenant_id", "*"));
        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void entitledDomains_acceptsEvenWithWrongTenantId() {
        var validator = ServiceLevelOAuth2Config.tenantClaimValidator("scm");
        Jwt jwt = buildJwt(Map.of(
                "tenant_id", "globex",
                "entitled_domains", List.of("scm", "wms")));
        assertThat(validator.validate(jwt).hasErrors()).isFalse();
    }

    @Test
    void noEntitlementAndWrongTenantId_isRejected() {
        var validator = ServiceLevelOAuth2Config.tenantClaimValidator("scm");
        Jwt jwt = buildJwt(Map.of(
                "tenant_id", "globex",
                "entitled_domains", List.of("wms")));
        assertThat(validator.validate(jwt).hasErrors()).isTrue();
    }

    private static Jwt buildJwt(Map<String, Object> claims) {
        Map<String, Object> headers = Map.of("alg", "RS256");
        Instant now = Instant.now();
        return Jwt.withTokenValue("token")
                .headers(h -> h.putAll(headers))
                .claims(c -> c.putAll(claims))
                .issuedAt(now)
                .expiresAt(now.plusSeconds(3600))
                .build();
    }
}
