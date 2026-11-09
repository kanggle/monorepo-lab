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
 * Unit tests for {@code SecurityConfig#jwtAuthenticationConverter}.
 *
 * <p>TASK-MONO-827 (ADR-MONO-083 · owner decision Q1, 2026-11-09 UTC): authorities are the token's
 * {@code roles} / {@code role} claims and nothing else. The converter used to synthesise
 * {@code ROLE_WMS_VIEWER} from {@code entitled_domains ∋ "wms"} (TASK-MONO-162); these cells pin
 * that it no longer does, for every claim shape the old synthesis reacted to. The end-to-end
 * consequence (subscription only → dashboards 403) is pinned through the real chain by
 * {@code DashboardBusinessRoleGateSliceTest}.
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
    @DisplayName("MONO-827: entitled_domains ∋ wms + 역할 없음 → 권한 0 (VIEWER 합성 없음)")
    void entitlementAloneGrantsNothing() {
        Set<String> auth = authorities(Map.of(
                "tenant_id", "globex-corp",
                "entitled_domains", List.of("wms", "finance"),
                "sub", "u"));
        assertThat(auth).isEmpty();
    }

    @Test
    @DisplayName("MONO-827: 업무 역할 토큰 → 그 역할만 (entitled_domains 가 VIEWER 를 덧붙이지 않는다)")
    void businessRolesPassThroughWithoutViewer() {
        Set<String> auth = authorities(Map.of(
                "tenant_id", "globex-corp",
                "entitled_domains", List.of("wms"),
                "roles", List.of("INVENTORY_READ"),
                "sub", "u"));
        assertThat(auth).containsExactly("ROLE_INVENTORY_READ");
    }

    @Test
    @DisplayName("roles 클레임은 그대로 ROLE_ 접두로 풀린다")
    void explicitRolesResolve() {
        Set<String> auth = authorities(Map.of(
                "tenant_id", "wms",
                "roles", List.of("WMS_ADMIN"),
                "sub", "u"));
        assertThat(auth).containsExactly("ROLE_WMS_ADMIN");
    }

    @Test
    @DisplayName("레거시 role 클레임(문자열·배열)도 ROLE_ 접두로 풀린다")
    void legacyRoleClaimResolves() {
        assertThat(authorities(Map.of("tenant_id", "wms", "role", "WMS_OPERATOR", "sub", "u")))
                .containsExactly("ROLE_WMS_OPERATOR");
        assertThat(authorities(Map.of("tenant_id", "wms", "role", List.of("WMS_VIEWER"), "sub", "u")))
                .containsExactly("ROLE_WMS_VIEWER");
    }
}
