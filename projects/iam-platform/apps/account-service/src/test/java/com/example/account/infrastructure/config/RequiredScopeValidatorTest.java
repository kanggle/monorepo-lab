package com.example.account.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-514 — {@link RequiredScopeValidator} unit tests (the {@code internal.invoke} scope
 * discriminator that distinguishes a GAP system credential from a user token on the shared IAM issuer).
 */
@DisplayName("RequiredScopeValidator — internal.invoke scope discriminator (TASK-BE-514)")
class RequiredScopeValidatorTest {

    private static final String REQUIRED = "internal.invoke";

    private static Jwt jwtWithScopeClaim(String claimName, Object claimValue) {
        Jwt.Builder b = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("admin-service-client")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (claimName != null) {
            b.claim(claimName, claimValue);
        }
        return b.build();
    }

    @Test
    @DisplayName("scope 문자열에 internal.invoke 단독 → success")
    void singleScopeString_passes() {
        OAuth2TokenValidatorResult r = new RequiredScopeValidator(REQUIRED)
                .validate(jwtWithScopeClaim("scope", "internal.invoke"));
        assertThat(r.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("공백 구분 다중 scope 에 internal.invoke 포함 → success")
    void spaceDelimitedScopesContaining_passes() {
        OAuth2TokenValidatorResult r = new RequiredScopeValidator(REQUIRED)
                .validate(jwtWithScopeClaim("scope", "profile internal.invoke email"));
        assertThat(r.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("scope 가 List 형태로 internal.invoke 포함 → success")
    void listScopeContaining_passes() {
        OAuth2TokenValidatorResult r = new RequiredScopeValidator(REQUIRED)
                .validate(jwtWithScopeClaim("scope", List.of("profile", "internal.invoke")));
        assertThat(r.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("scp fallback 클레임에 internal.invoke → success")
    void scpFallbackClaim_passes() {
        OAuth2TokenValidatorResult r = new RequiredScopeValidator(REQUIRED)
                .validate(jwtWithScopeClaim("scp", List.of("internal.invoke")));
        assertThat(r.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("다른 scope 만(internal.invoke 없음) → failure (user 토큰 지문)")
    void wrongScopeOnly_fails() {
        OAuth2TokenValidatorResult r = new RequiredScopeValidator(REQUIRED)
                .validate(jwtWithScopeClaim("scope", "profile email openid"));
        assertThat(r.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("scope 클레임 자체가 없음 → failure")
    void noScopeClaim_fails() {
        OAuth2TokenValidatorResult r = new RequiredScopeValidator(REQUIRED)
                .validate(jwtWithScopeClaim(null, null));
        assertThat(r.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("scope 클레임이 공백 문자열 → failure")
    void blankScopeClaim_fails() {
        OAuth2TokenValidatorResult r = new RequiredScopeValidator(REQUIRED)
                .validate(jwtWithScopeClaim("scope", "   "));
        assertThat(r.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("부분 문자열 오탐 방지 — 'internal.invoke.extra' 는 매치 아님")
    void substringLikeScope_fails() {
        OAuth2TokenValidatorResult r = new RequiredScopeValidator(REQUIRED)
                .validate(jwtWithScopeClaim("scope", "internal.invoke.extra other.internal.invoke"));
        assertThat(r.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("required scope 가 blank 로 구성됨 → 토큰이 scope 를 가져도 fail-closed")
    void blankRequiredScope_failsClosedEvenWithScope() {
        OAuth2TokenValidatorResult r = new RequiredScopeValidator("   ")
                .validate(jwtWithScopeClaim("scope", "internal.invoke"));
        assertThat(r.hasErrors())
                .as("blank required-scope config must reject, never admit-all")
                .isTrue();
    }

    @Test
    @DisplayName("required scope 가 null 로 구성됨 → fail-closed")
    void nullRequiredScope_failsClosed() {
        OAuth2TokenValidatorResult r = new RequiredScopeValidator(null)
                .validate(jwtWithScopeClaim("scope", "internal.invoke"));
        assertThat(r.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("에러 코드는 invalid_token (엔트리포인트 401 매핑)")
    void failureUsesInvalidTokenErrorCode() {
        OAuth2TokenValidatorResult r = new RequiredScopeValidator(REQUIRED)
                .validate(jwtWithScopeClaim("scope", "profile"));
        assertThat(r.getErrors()).isNotEmpty();
        assertThat(r.getErrors().iterator().next().getErrorCode()).isEqualTo("invalid_token");
    }

    @Test
    @DisplayName("숫자/비문자 claim 형태 → 안전하게 failure (예외 없음)")
    void nonStringNonCollectionClaim_failsSafely() {
        OAuth2TokenValidatorResult r = new RequiredScopeValidator(REQUIRED)
                .validate(jwtWithScopeClaim("scope", Map.of("unexpected", "shape")));
        assertThat(r.hasErrors()).isTrue();
    }
}
