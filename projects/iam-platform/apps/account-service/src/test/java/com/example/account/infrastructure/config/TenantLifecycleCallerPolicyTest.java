package com.example.account.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.authentication.preauth.PreAuthenticatedAuthenticationToken;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-640 — {@link TenantLifecycleCallerPolicy}: the {@code sub} allow-list, its fail-closed parsing
 * (AC-3: an ABSENT or blank value refuses every JWT caller) and the bypass principal being admitted only
 * while the bypass is actually on.
 */
@DisplayName("TenantLifecycleCallerPolicy — sub 허용 목록 · fail-closed · bypass 조건 (TASK-BE-640)")
class TenantLifecycleCallerPolicyTest {

    private static JwtAuthenticationToken jwt(String subject) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256")
                .claim("scope", "internal.invoke")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (subject != null) {
            b.subject(subject);
        }
        return new JwtAuthenticationToken(b.build(), List.of(new SimpleGrantedAuthority("SCOPE_internal.invoke")));
    }

    private static PreAuthenticatedAuthenticationToken bypassPrincipal() {
        return new PreAuthenticatedAuthenticationToken(InternalApiFilter.INTERNAL_PRINCIPAL, "n/a",
                List.of(new SimpleGrantedAuthority(InternalApiFilter.INTERNAL_AUTHORITY)));
    }

    @Test
    @DisplayName("목록에 있는 sub → 허용 · 없는 sub · sub 없음 · 계정 UUID → 거절")
    void judgesBySubject() {
        TenantLifecycleCallerPolicy p = TenantLifecycleCallerPolicy.fromCsv(
                " admin-service-client , auth-service-client ", false);
        assertThat(p.allows(jwt("admin-service-client"))).isTrue();
        assertThat(p.allows(jwt("auth-service-client"))).isTrue();
        assertThat(p.allows(jwt("product-service-client"))).isFalse();
        assertThat(p.allows(jwt("0199de70-0000-7000-8000-000000000640"))).isFalse();
        assertThat(p.allows(jwt(null))).isFalse();
        assertThat(p.allows(jwt("ADMIN-SERVICE-CLIENT"))).as("exact match, case-sensitive").isFalse();
    }

    @Test
    @DisplayName("🔴 AC-3: 값 없음(null) · 빈 문자열 · 공백 · 쉼표뿐 → 빈 목록 → 모든 JWT 호출자 거절")
    void absentOrBlank_refusesEveryone() {
        for (String csv : new String[]{null, "", "   ", " , ,"}) {
            TenantLifecycleCallerPolicy p = TenantLifecycleCallerPolicy.fromCsv(csv, false);
            assertThat(p.allowedClientIds()).as("csv=[%s]", csv).isEmpty();
            assertThat(p.allows(jwt("admin-service-client"))).as("csv=[%s]", csv).isFalse();
            assertThat(p.allows(jwt("auth-service-client"))).as("csv=[%s]", csv).isFalse();
        }
    }

    @Test
    @DisplayName("bypass 주체는 bypass 가 켜졌을 때만 통과 — 꺼지면(운영) 거절")
    void bypassPrincipal_onlyWhenBypassOn() {
        assertThat(TenantLifecycleCallerPolicy.fromCsv("admin-service-client", true).allows(bypassPrincipal()))
                .isTrue();
        assertThat(TenantLifecycleCallerPolicy.fromCsv("admin-service-client", false).allows(bypassPrincipal()))
                .isFalse();
    }

    @Test
    @DisplayName("bypass 가 켜져 있어도 JWT 호출자는 목록으로 판정")
    void bypassOn_jwtStillJudged() {
        TenantLifecycleCallerPolicy p = TenantLifecycleCallerPolicy.fromCsv("admin-service-client", true);
        assertThat(p.allows(jwt("product-service-client"))).isFalse();
        assertThat(p.allows(jwt("admin-service-client"))).isTrue();
    }

    @Test
    @DisplayName("인증 없음 · 익명 → 거절")
    void unauthenticated_refused() {
        TenantLifecycleCallerPolicy p = TenantLifecycleCallerPolicy.fromCsv("admin-service-client", true);
        assertThat(p.allows(null)).isFalse();
        assertThat(p.allows(new AnonymousAuthenticationToken("k", "anonymousUser",
                List.of(new SimpleGrantedAuthority("ROLE_ANONYMOUS"))))).isFalse();
    }
}
