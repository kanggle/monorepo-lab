package com.example.finance.account.infrastructure.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the account {@link ActorContextJwtAuthenticationConverter}. Two concerns:
 *
 * <ul>
 *   <li>scope-lifting (TASK-FIN-BE-046): the OAuth2 {@code scope} claim becomes {@code SCOPE_*}
 *       authorities so {@link SecurityConfig} can require {@code finance.write} per endpoint;</li>
 *   <li>entitlement-trust READ authority (TASK-FIN-BE-048): a finance-entitled token
 *       ({@code entitled_domains ∋ "finance"}) is granted {@code ROLE_FINANCE_VIEWER} — READ
 *       visibility only — mirroring the WMS {@code ROLE_WMS_VIEWER} synthesis (TASK-MONO-162).</li>
 * </ul>
 */
@DisplayName("account ActorContextJwtAuthenticationConverter — scope lifting + entitlement-trust VIEWER")
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

    // ---- scope lifting (TASK-FIN-BE-046) ---------------------------------------------------------

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
}
