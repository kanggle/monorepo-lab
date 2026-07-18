package com.example.finance.ledger.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the ledger {@link ActorContextJwtAuthenticationConverter} scope-lifting
 * (TASK-FIN-BE-047). The converter must lift the OAuth2 {@code scope} claim into {@code SCOPE_*}
 * authorities so {@link SecurityConfig} can require {@code finance.write} per endpoint — before this
 * fix the ledger converter lifted only roles, so the {@code scope} claim was invisible to
 * authorization and any authenticated finance token could write.
 */
@DisplayName("ledger ActorContextJwtAuthenticationConverter — scope lifting (TASK-FIN-BE-047)")
class ActorContextJwtAuthenticationConverterTest {

    private final ActorContextJwtAuthenticationConverter converter =
            new ActorContextJwtAuthenticationConverter();

    private static Jwt jwt(Consumer<Jwt.Builder> extra) {
        Jwt.Builder b = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("user-1")
                .claim("tenant_id", "finance")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        extra.accept(b);
        return b.build();
    }

    private List<String> authorities(Jwt jwt) {
        AbstractAuthenticationToken token = converter.convert(jwt);
        return token.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
    }

    @Test
    @DisplayName("scope 클레임(List)이 SCOPE_* 권한으로 리프트된다")
    void listScopeLifted() {
        assertThat(authorities(jwt(b -> b.claim("scope", List.of("finance.write")))))
                .contains("SCOPE_finance.write");
    }

    @Test
    @DisplayName("scope 클레임(공백 구분 문자열)도 리프트된다")
    void spaceDelimitedScopeLifted() {
        assertThat(authorities(jwt(b -> b.claim("scope", "finance.read finance.write"))))
                .contains("SCOPE_finance.read", "SCOPE_finance.write");
    }

    @Test
    @DisplayName("scp fallback 클레임도 리프트된다")
    void scpFallbackLifted() {
        assertThat(authorities(jwt(b -> b.claim("scp", List.of("finance.write")))))
                .contains("SCOPE_finance.write");
    }

    @Test
    @DisplayName("roles 와 scope 가 함께 리프트된다 (ROLE_* + SCOPE_*)")
    void rolesAndScopesBothLifted() {
        List<String> auths = authorities(jwt(b -> b
                .claim("roles", List.of("FINANCE_OPERATOR"))
                .claim("scope", List.of("finance.read"))));
        assertThat(auths).contains("ROLE_FINANCE_OPERATOR", "SCOPE_finance.read");
    }

    @Test
    @DisplayName("scope 클레임이 없으면 SCOPE_* 권한도 없다 (roles 는 그대로)")
    void noScopeClaimNoScopeAuthority() {
        List<String> auths = authorities(jwt(b -> b.claim("roles", List.of("OPERATOR"))));
        assertThat(auths).contains("ROLE_OPERATOR");
        assertThat(auths).noneMatch(a -> a.startsWith("SCOPE_"));
    }

    // ---- entitlement-trust READ authority (TASK-FIN-BE-048) --------------------------------------

    @Test
    @DisplayName("entitled_domains ∋ finance → ROLE_FINANCE_VIEWER 가 합성된다 (scope·role 없어도)")
    void financeEntitledGrantsViewerRole() {
        List<String> auths = authorities(jwt(b -> b.claim("entitled_domains", List.of("finance", "wms"))));
        assertThat(auths).contains(ActorContextJwtAuthenticationConverter.VIEWER_ROLE);
        // read-visibility only — no scope authority is synthesised, so writes stay gated
        assertThat(auths).noneMatch(a -> a.startsWith("SCOPE_"));
    }

    @Test
    @DisplayName("entitled_domains 클레임이 없으면 VIEWER 권한도 없다")
    void notEntitledGetsNoViewer() {
        List<String> auths = authorities(jwt(b -> b.claim("scope", List.of("finance.read"))));
        assertThat(auths).doesNotContain(ActorContextJwtAuthenticationConverter.VIEWER_ROLE);
    }

    @Test
    @DisplayName("다른 도메인에만 entitled(=[wms]) 이면 finance VIEWER 권한 없다")
    void entitledElsewhereGetsNoFinanceViewer() {
        List<String> auths = authorities(jwt(b -> b.claim("entitled_domains", List.of("wms"))));
        assertThat(auths).doesNotContain(ActorContextJwtAuthenticationConverter.VIEWER_ROLE);
    }

    @Test
    @DisplayName("entitled(=[finance]) 토큰은 VIEWER 만 갖고 WRITE-계열 권한(SCOPE_/OPERATOR)은 없다")
    void financeEntitledNoScopeNoRoleGetsOnlyViewer() {
        List<String> auths = authorities(jwt(b -> b.claim("entitled_domains", List.of("finance"))));
        assertThat(auths).containsExactly(ActorContextJwtAuthenticationConverter.VIEWER_ROLE);
    }

    // ---- platform super-admin wildcard READ authority (TASK-FIN-BE-049) --------------------------

    @Test
    @DisplayName("tenant_id='*' (super-admin wildcard, no scope/role/entitlement) → ROLE_FINANCE_SUPERADMIN_READ 만 합성")
    void wildcardTenantGrantsSuperadminReadRole() {
        List<String> auths = authorities(jwt(b -> b.claim("tenant_id", "*")));
        // read-visibility only — exactly the wildcard-read role, no SCOPE_* / VIEWER / operator role
        assertThat(auths).containsExactly(ActorContextJwtAuthenticationConverter.SUPERADMIN_READ_ROLE);
        assertThat(auths).noneMatch(a -> a.startsWith("SCOPE_"));
    }

    @Test
    @DisplayName("tenant_id 가 '*' 아니면 (finance) SUPERADMIN_READ 권한 없다 (와일드카드에만 엄격 키잉)")
    void nonWildcardTenantGetsNoSuperadminRead() {
        List<String> auths = authorities(jwt(b -> b.claim("roles", List.of("OPERATOR"))));
        assertThat(auths).doesNotContain(ActorContextJwtAuthenticationConverter.SUPERADMIN_READ_ROLE);
    }

    // ---- convert() guard clauses (TASK-FIN-BE-052) -----------------------------------------------

    @Test
    @DisplayName("sub 클레임이 없으면 IllegalStateException (sub 가드; tenant_id 는 존재)")
    void missingSubClaimThrows() {
        Jwt noSub = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .claim("tenant_id", "finance")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        assertThatThrownBy(() -> converter.convert(noSub))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sub");
    }

    @Test
    @DisplayName("tenant_id 클레임이 없으면 IllegalStateException (tenant_id 가드; sub 는 존재)")
    void missingTenantIdClaimThrows() {
        Jwt noTenant = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("user-1")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300))
                .build();
        assertThatThrownBy(() -> converter.convert(noTenant))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("tenant_id");
    }

    // ---- extractRoles claim shapes (TASK-FIN-BE-052) ---------------------------------------------

    @Test
    @DisplayName("role 단수 alias 클레임도 ROLE_* 로 리프트된다 (roles 없을 때 fallback)")
    void roleSingularAliasLifted() {
        assertThat(authorities(jwt(b -> b.claim("role", List.of("OPERATOR")))))
                .contains("ROLE_OPERATOR");
    }

    @Test
    @DisplayName("roles 가 구분자 문자열(공백/쉼표)이면 다중 ROLE_* 로 분해된다")
    void delimitedRolesLifted() {
        assertThat(authorities(jwt(b -> b.claim("roles", "OPERATOR ADMIN"))))
                .contains("ROLE_OPERATOR", "ROLE_ADMIN");
    }
}
