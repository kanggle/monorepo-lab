package com.example.security.infrastructure.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-422 — asserts the <em>actual</em> validator chain that
 * {@link InternalJwtDecoderConfig#internalJwtDecoder} enforces on {@code /internal/**} (built by
 * {@link InternalJwtDecoderConfig#internalTokenValidator}). A valid-issuer token that lacks
 * {@code internal.invoke} (a user token on the shared IAM issuer) must be rejected by the real chain,
 * which {@link InternalAuthFilter} then surfaces as 403 PERMISSION_DENIED.
 *
 * <p>security-service {@code /internal/**} slice/integration tests run under the {@code test} profile,
 * which turns the {@link InternalAuthFilter} bypass ON and never exercises the decoder — hence this
 * decoder-chain assertion is where the scope gate is actually verified.
 */
@DisplayName("InternalJwtDecoderConfig.internalTokenValidator() — real /internal/** chain enforces issuer + internal.invoke (TASK-MONO-422)")
class InternalJwtDecoderConfigTest {

    private static final String ISSUER = "http://localhost:8081";

    private static OAuth2TokenValidator<Jwt> chain() {
        return InternalJwtDecoderConfig.internalTokenValidator(ISSUER, "internal.invoke");
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
    @DisplayName("올바른 issuer + internal.invoke → 통과")
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
    @DisplayName("올바른 issuer + 다른 scope → 거부")
    void validIssuerWrongScope_rejected() {
        assertThat(chain().validate(token(ISSUER, "profile email")).hasErrors()).isTrue();
    }

    @Test
    @DisplayName("issuer 검증도 체인에 남아있음 — 잘못된 issuer + 올바른 scope → 거부")
    void wrongIssuer_stillRejected() {
        assertThat(chain().validate(token("http://attacker", "internal.invoke")).hasErrors()).isTrue();
    }
}
