package com.wms.admin.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@code jwtAuthenticationConverter} entitlement-trust
 * READ-visibility synthesis (ADR-MONO-019 § D5, ADR-MONO-020 D4, TASK-MONO-162).
 *
 * <p>A wms-entitled token ({@code entitled_domains ∋ "wms"}) is granted
 * {@code ROLE_WMS_VIEWER} so the {@code @PreAuthorize("hasRole('WMS_VIEWER')")}
 * READ dashboards pass — but ONLY the VIEWER role: the WRITE-gated roles
 * (WMS_OPERATOR/WMS_ADMIN/WMS_SUPERADMIN) are never synthesised, so an
 * entitlement-only token cannot satisfy a WRITE check. Existing role/scope
 * tokens authorize exactly as before (net-zero).
 */
class SecurityConfigConverterTest {

    private Set<String> authorities(Map<String, Object> claims) {
        Jwt jwt = new Jwt("t", Instant.now(), Instant.now().plusSeconds(60),
                Map.of("alg", "RS256"), claims);
        AbstractAuthenticationToken token =
                SecurityConfig.jwtAuthenticationConverter().convert(jwt);
        return token.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("entitled (entitled_domains ∋ wms) → ROLE_WMS_VIEWER granted (READ passes)")
    void entitledGrantsViewer() {
        Set<String> auth = authorities(Map.of(
                "tenant_id", "globex-corp",
                "entitled_domains", List.of("wms", "finance"),
                "sub", "u"));
        assertThat(auth).contains("ROLE_WMS_VIEWER");
    }

    @Test
    @DisplayName("entitlement-only token does NOT receive WRITE roles (WRITE stays denied)")
    void entitledDoesNotGrantWriteRoles() {
        Set<String> auth = authorities(Map.of(
                "tenant_id", "globex-corp",
                "entitled_domains", List.of("wms"),
                "sub", "u"));
        assertThat(auth)
                .contains("ROLE_WMS_VIEWER")
                .doesNotContain("ROLE_WMS_OPERATOR", "ROLE_WMS_ADMIN", "ROLE_WMS_SUPERADMIN");
    }

    @Test
    @DisplayName("neither role nor wms entitlement → no synthesised authority (denied)")
    void neitherRoleNorEntitlementDenied() {
        Set<String> auth = authorities(Map.of(
                "tenant_id", "acme",
                "entitled_domains", List.of("finance", "scm"),
                "sub", "u"));
        assertThat(auth).doesNotContain("ROLE_WMS_VIEWER");
    }

    @Test
    @DisplayName("net-zero: explicit roles claim still resolves (entitlement adds, never replaces)")
    void explicitRolesUnaffected() {
        Set<String> auth = authorities(Map.of(
                "tenant_id", "wms",
                "roles", List.of("WMS_ADMIN"),
                "sub", "u"));
        assertThat(auth).contains("ROLE_WMS_ADMIN");
        // No entitled_domains claim → no synthesised VIEWER (pure legacy path).
        assertThat(auth).doesNotContain("ROLE_WMS_VIEWER");
    }

    @Test
    @DisplayName("claim-shape safety: non-list entitled_domains → no VIEWER (fail-closed)")
    void malformedEntitlementNoViewer() {
        Set<String> auth = authorities(Map.of(
                "tenant_id", "acme",
                "entitled_domains", "wms",
                "sub", "u"));
        assertThat(auth).doesNotContain("ROLE_WMS_VIEWER");
    }
}
