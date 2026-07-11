package com.example.apigateway.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * {@link JwtClaims#role(Jwt)} is the one that matters. Its precedence and its empty-string
 * fallback were duplicated in three gateways; the fallback in particular is a security
 * contract (see the method's javadoc), and a security contract with three definitions is one
 * that will eventually have two.
 */
@DisplayName("JwtClaims — 클레임 추출 규칙")
class JwtClaimsTest {

    private static Jwt jwt(Map<String, Object> claims) {
        Jwt.Builder b = Jwt.withTokenValue("token")
                .header("alg", "RS256")
                .issuer("http://iam.local")
                .subject("user-42")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60));
        claims.forEach(b::claim);
        return b.build();
    }

    @Test
    @DisplayName("roles 배열이 role 문자열보다 우선하며, 콤마로 합쳐진다")
    void rolesArrayTakesPrecedenceAndJoinsOnComma() {
        assertThat(JwtClaims.role(jwt(Map.of(
                "roles", List.of("MASTER_READ", "MASTER_WRITE"),
                "role", "IGNORED"))))
                .isEqualTo("MASTER_READ,MASTER_WRITE");
    }

    @Test
    @DisplayName("roles 가 없으면 role 문자열")
    void fallsBackToRoleString() {
        assertThat(JwtClaims.role(jwt(Map.of("role", "MASTER_WRITE")))).isEqualTo("MASTER_WRITE");
    }

    @Test
    @DisplayName("빈 roles 배열은 role 문자열로 강등된다")
    void emptyRolesArrayFallsBackToRoleString() {
        assertThat(JwtClaims.role(jwt(Map.of("roles", List.of(), "role", "READER"))))
                .isEqualTo("READER");
    }

    @Test
    @DisplayName("공백 role 문자열은 역할 없음으로 취급")
    void blankRoleStringIsNoRole() {
        assertThat(JwtClaims.role(jwt(Map.of("role", "   ")))).isEmpty();
    }

    @Test
    @DisplayName("역할이 전혀 없으면 null 이 아니라 빈 문자열 — 헤더에 그대로 쓸 수 있어야 한다")
    void noRoleYieldsEmptyStringNeverNull() {
        assertThat(JwtClaims.role(jwt(Map.of()))).isNotNull().isEmpty();
    }

    @Test
    void extractsSubjectEmailTenantAndScope() {
        Jwt jwt = jwt(Map.of("email", "u@example.com", "tenant_id", "wms", "scope", "read write"));

        assertThat(JwtClaims.subject(jwt)).isEqualTo("user-42");
        assertThat(JwtClaims.email(jwt)).isEqualTo("u@example.com");
        assertThat(JwtClaims.tenantId(jwt)).isEqualTo("wms");
        assertThat(JwtClaims.scope(jwt)).isEqualTo("read write");
    }

    @Test
    @DisplayName("부재 클레임은 null — SKIP_IF_NULL 매핑이 이 값에 의존한다")
    void absentClaimsAreNull() {
        Jwt jwt = jwt(Map.of());

        assertThat(JwtClaims.email(jwt)).isNull();
        assertThat(JwtClaims.tenantId(jwt)).isNull();
        assertThat(JwtClaims.scope(jwt)).isNull();
    }
}
