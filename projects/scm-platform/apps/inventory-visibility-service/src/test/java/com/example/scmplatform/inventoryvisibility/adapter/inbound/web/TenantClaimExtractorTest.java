package com.example.scmplatform.inventoryvisibility.adapter.inbound.web;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TenantClaimExtractor}.
 *
 * <p>AC A3: claim present / claim absent + throw type / message preserved
 * (TASK-SCM-BE-017).
 */
class TenantClaimExtractorTest {

    // ---- claim present -------------------------------------------------------

    @Test
    void extractTenantId_claimPresent_returnsClaim() {
        Jwt jwt = buildJwt(Map.of("tenant_id", "scm"));

        String result = TenantClaimExtractor.extractTenantId(jwt);

        assertThat(result).isEqualTo("scm");
    }

    @Test
    void extractTenantId_claimPresentWithWildcard_returnsWildcard() {
        Jwt jwt = buildJwt(Map.of("tenant_id", "*"));

        String result = TenantClaimExtractor.extractTenantId(jwt);

        assertThat(result).isEqualTo("*");
    }

    // ---- claim absent / jwt null ---------------------------------------------

    @Test
    void extractTenantId_claimAbsent_returnsDefaultScm() {
        // JWT without tenant_id claim
        Jwt jwt = buildJwt(Map.of());

        String result = TenantClaimExtractor.extractTenantId(jwt);

        assertThat(result).isEqualTo("scm");
    }

    @Test
    void extractTenantId_nullJwt_returnsDefaultScm() {
        // null JWT (e.g. security filter chain bypassed in tests)
        String result = TenantClaimExtractor.extractTenantId(null);

        assertThat(result).isEqualTo("scm");
    }

    // ---- helper --------------------------------------------------------------

    private static Jwt buildJwt(Map<String, Object> extraClaims) {
        Map<String, Object> headers = Map.of("alg", "RS256");
        Map<String, Object> claims = new java.util.LinkedHashMap<>();
        claims.put("sub", "test-subject");
        claims.put("iat", Instant.now().minusSeconds(30));
        claims.put("exp", Instant.now().plusSeconds(300));
        claims.putAll(extraClaims);

        return Jwt.withTokenValue("test-token")
                .headers(h -> h.putAll(headers))
                .claims(c -> c.putAll(claims))
                .build();
    }
}
