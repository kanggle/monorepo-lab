package com.example.apigateway.filter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/** {@link RoleAdmissions#roleOrScope()} — the credential-presence predicate in isolation. */
@DisplayName("RoleAdmissions.roleOrScope — role 또는 scope 가 있으면 admit")
class RoleAdmissionsTest {

    private final Predicate<Jwt> admits = RoleAdmissions.roleOrScope();

    @Test
    void admitsWhenRolesArrayNonEmpty() {
        assertThat(admits.test(jwt(Map.of("roles", List.of("ERP_OPERATOR"))))).isTrue();
    }

    @Test
    void admitsWhenSingularRolePresent() {
        assertThat(admits.test(jwt(Map.of("role", "OPERATOR")))).isTrue();
    }

    @Test
    void admitsWhenScopePresentButNoRole() {
        assertThat(admits.test(jwt(Map.of("scope", "scm.read")))).isTrue();
    }

    @Test
    @DisplayName("role 도 scope 도 없으면 거부")
    void rejectsWhenNeitherRoleNorScope() {
        assertThat(admits.test(jwt(Map.of()))).isFalse();
    }

    @Test
    @DisplayName("빈 roles 배열 + blank scope → 거부")
    void rejectsEmptyRolesAndBlankScope() {
        assertThat(admits.test(jwt(Map.of("roles", List.of(), "scope", "  ")))).isFalse();
    }

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .header("alg", "none")
                .issuer("test")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        claims.forEach(b::claim);
        return b.build();
    }
}
