package com.example.account.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-514 — asserts the <em>actual</em> validator chain that
 * {@link SecurityConfig#internalJwtDecoder()} enforces on {@code /internal/**} (built by
 * {@link SecurityConfig#internalTokenValidator()}). This is the wiring proof: it exercises the real
 * chain SecurityConfig composes (issuer/timestamp default + {@link RequiredScopeValidator}), not a
 * re-implemented copy — so a token that lacks {@code internal.invoke} is rejected end-to-end at the
 * decoder level.
 *
 * <p>account-service {@code /internal/**} integration tests run under the {@code test} profile, which
 * turns the {@code InternalApiFilter} bypass ON and never exercises the JWT decoder — hence this
 * decoder-chain-level assertion is where the scope gate is actually verified.
 */
@DisplayName("SecurityConfig.internalTokenValidator() — real /internal/** chain enforces issuer + internal.invoke scope (TASK-BE-514)")
class SecurityConfigInternalValidatorTest {

    private static final String ISSUER = "http://localhost:8081";

    private OAuth2TokenValidator<Jwt> chain() {
        SecurityConfig config = new SecurityConfig(new MockEnvironment());
        ReflectionTestUtils.setField(config, "jwtIssuer", ISSUER);
        ReflectionTestUtils.setField(config, "requiredScope", "internal.invoke");
        return config.internalTokenValidator();
    }

    private static Jwt token(String issuer, String scope) {
        Jwt.Builder b = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject("admin-service-client")
                .issuer(issuer)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (scope != null) {
            b.claim("scope", scope);
        }
        return b.build();
    }

    @Test
    @DisplayName("올바른 issuer + internal.invoke scope (시스템 워크로드 토큰) → 통과")
    void systemWorkloadToken_passes() {
        OAuth2TokenValidatorResult r = chain().validate(token(ISSUER, "internal.invoke"));
        assertThat(r.hasErrors()).isFalse();
    }

    @Test
    @DisplayName("올바른 issuer 지만 scope 없음(유저 토큰 지문) → 거부")
    void validIssuerNoScope_rejected() {
        OAuth2TokenValidatorResult r = chain().validate(token(ISSUER, null));
        assertThat(r.hasErrors())
                .as("a valid-issuer token without internal.invoke is a user token — must be rejected")
                .isTrue();
    }

    @Test
    @DisplayName("올바른 issuer + 다른 scope(profile) → 거부")
    void validIssuerWrongScope_rejected() {
        OAuth2TokenValidatorResult r = chain().validate(token(ISSUER, "profile email"));
        assertThat(r.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("issuer 검증도 체인에 남아있음 — 잘못된 issuer + 올바른 scope → 거부")
    void wrongIssuer_stillRejected() {
        OAuth2TokenValidatorResult r = chain().validate(token("http://attacker", "internal.invoke"));
        assertThat(r.hasErrors())
                .as("the issuer/timestamp default validator is retained alongside the scope gate")
                .isTrue();
    }
}
