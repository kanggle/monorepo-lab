package com.example.web.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RequiredScopeValidator} — the shared workload-scope discriminator
 * (TASK-MONO-422; promoted from the account-service local copy, TASK-BE-514).
 */
@DisplayName("RequiredScopeValidator — required-scope discriminator")
class RequiredScopeValidatorTest {

    private static final String REQUIRED = "internal.invoke";

    private static Jwt jwtWithScopeClaim(String claimName, Object claimValue) {
        Jwt.Builder b = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("some-workload-client")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (claimName != null) {
            b.claim(claimName, claimValue);
        }
        return b.build();
    }

    @Test
    @DisplayName("scope 문자열에 required 단독 → success")
    void singleScopeString_passes() {
        assertThat(new RequiredScopeValidator(REQUIRED)
                .validate(jwtWithScopeClaim("scope", "internal.invoke")).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("공백 구분 다중 scope 에 required 포함 → success")
    void spaceDelimitedScopesContaining_passes() {
        assertThat(new RequiredScopeValidator(REQUIRED)
                .validate(jwtWithScopeClaim("scope", "profile internal.invoke email")).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("scope 가 List 형태로 required 포함 → success")
    void listScopeContaining_passes() {
        assertThat(new RequiredScopeValidator(REQUIRED)
                .validate(jwtWithScopeClaim("scope", List.of("profile", "internal.invoke"))).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("scp fallback 클레임에 required → success")
    void scpFallbackClaim_passes() {
        assertThat(new RequiredScopeValidator(REQUIRED)
                .validate(jwtWithScopeClaim("scp", List.of("internal.invoke"))).hasErrors()).isFalse();
    }

    @Test
    @DisplayName("다른 scope 만(required 없음) → failure")
    void wrongScopeOnly_fails() {
        assertThat(new RequiredScopeValidator(REQUIRED)
                .validate(jwtWithScopeClaim("scope", "profile email openid")).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("scope 클레임 자체가 없음 → failure")
    void noScopeClaim_fails() {
        assertThat(new RequiredScopeValidator(REQUIRED)
                .validate(jwtWithScopeClaim(null, null)).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("scope 클레임이 공백 문자열 → failure")
    void blankScopeClaim_fails() {
        assertThat(new RequiredScopeValidator(REQUIRED)
                .validate(jwtWithScopeClaim("scope", "   ")).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("부분 문자열 오탐 방지 — 'internal.invoke.extra' 는 매치 아님")
    void substringLikeScope_fails() {
        assertThat(new RequiredScopeValidator(REQUIRED)
                .validate(jwtWithScopeClaim("scope", "internal.invoke.extra other.internal.invoke")).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("required scope 가 blank 로 구성됨 → 토큰이 scope 를 가져도 fail-closed")
    void blankRequiredScope_failsClosedEvenWithScope() {
        assertThat(new RequiredScopeValidator("   ")
                .validate(jwtWithScopeClaim("scope", "internal.invoke")).hasErrors())
                .as("blank required-scope config must reject, never admit-all")
                .isTrue();
    }

    @Test
    @DisplayName("required scope 가 null 로 구성됨 → fail-closed")
    void nullRequiredScope_failsClosed() {
        assertThat(new RequiredScopeValidator(null)
                .validate(jwtWithScopeClaim("scope", "internal.invoke")).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("에러 코드는 invalid_token")
    void failureUsesInvalidTokenErrorCode() {
        OAuth2TokenValidatorResult r = new RequiredScopeValidator(REQUIRED)
                .validate(jwtWithScopeClaim("scope", "profile"));
        assertThat(r.getErrors()).isNotEmpty();
        assertThat(r.getErrors().iterator().next().getErrorCode()).isEqualTo("invalid_token");
    }

    @Test
    @DisplayName("비문자/비컬렉션 claim 형태 → 안전하게 failure (예외 없음)")
    void nonStringNonCollectionClaim_failsSafely() {
        assertThat(new RequiredScopeValidator(REQUIRED)
                .validate(jwtWithScopeClaim("scope", Map.of("unexpected", "shape"))).hasErrors()).isTrue();
    }
}
