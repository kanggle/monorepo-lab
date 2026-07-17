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
}
