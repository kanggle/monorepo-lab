package com.example.admin.infrastructure.config;

import com.example.web.security.RequiredScopeValidator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-MONO-716 — asserts the <em>actual</em> validator chain that
 * {@link SecurityConfig#internalJwtDecoder()} enforces on {@code /internal/**} (built by
 * {@link SecurityConfig#internalTokenValidator()}): the issuer/timestamp default plus the
 * {@link RequiredScopeValidator} workload discriminator.
 *
 * <p>Deliberately a copy of the sibling suites' shape (account-service / auth-service
 * {@code SecurityConfigInternalValidatorTest}, TASK-BE-514 / TASK-MONO-422) rather than a new
 * invention — four chains enforcing one axis should be asserted the same way, or a reader has to
 * learn four idioms to answer one question.
 *
 * <p>🔴 <b>This file is where the gate is actually verified, and the reason is not stylistic.</b>
 * admin-service's {@code /internal/**} integration tests run under the {@code test} profile, which
 * turns the {@code InternalApiFilter} bypass ON — under it the request is authenticated
 * <em>before</em> the bearer filter and the decoder is never consulted. So those suites stay green
 * whether this chain discriminates or not; a green run there is not evidence about this gate. That
 * is also why the assertions below go through {@code internalTokenValidator()} rather than
 * re-listing the validators: re-listing them would pass even if {@code internalJwtDecoder()}
 * stopped installing the chain.
 */
@DisplayName("SecurityConfig.internalTokenValidator() — 실제 /internal/** 사슬이 issuer + internal.invoke 를 강제한다 (TASK-MONO-716)")
class SecurityConfigInternalValidatorTest {

    private static final String ISSUER = "http://localhost:8081";

    private OAuth2TokenValidator<Jwt> chain() {
        SecurityConfig config = new SecurityConfig(new MockEnvironment());
        ReflectionTestUtils.setField(config, "internalJwtIssuer", ISSUER);
        ReflectionTestUtils.setField(config, "internalRequiredScope", "internal.invoke");
        return config.internalTokenValidator();
    }

    /**
     * @param subject the token's {@code sub}; a workload token carries its client id, an operator's
     *                browser token carries an account UUID — the two shapes this gate separates
     */
    private static Jwt token(String issuer, String subject, String scope) {
        Jwt.Builder b = Jwt.withTokenValue("t")
                .header("alg", "RS256")
                .subject(subject)
                .issuer(issuer)
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(300));
        if (scope != null) {
            b.claim("scope", scope);
        }
        return b.build();
    }

    @Test
    @DisplayName("대조군 — auth-service 워크로드 토큰(internal.invoke) → 통과")
    void systemWorkloadToken_passes() {
        OAuth2TokenValidatorResult r =
                chain().validate(token(ISSUER, "auth-service-client", "internal.invoke"));

        assertThat(r.hasErrors())
                .as("the measured caller (auth-service's AdminAssignmentClient, client "
                        + "`auth-service-client`, seeded with internal.invoke in V0019) must still pass")
                .isFalse();
    }

    /**
     * 🔴 The defect this ticket closes, stated as a test. Before MONO-716 this token was
     * <b>admitted</b>: the chain pinned only the issuer, and the IAM issuer is shared between
     * operator browser tokens and workload tokens — so an operator's own session token reached
     * {@code /internal/**}, where neither controller carries an authorization annotation.
     */
    @Test
    @DisplayName("bite — 운영자 브라우저 토큰 모양(같은 issuer · scope 없음) → 거절")
    void operatorBrowserToken_rejected() {
        OAuth2TokenValidatorResult r =
                chain().validate(token(ISSUER, UUID.randomUUID().toString(), null));

        assertThat(r.hasErrors())
                .as("a valid-issuer token without internal.invoke is a user token — before "
                        + "TASK-MONO-716 this reached /internal/** and nothing downstream stopped it")
                .isTrue();
    }

    /**
     * 🔵 The token that makes «scope present» insufficient on its own: another platform's workload
     * client is a real, registered {@code client_credentials} caller on the same issuer, and the
     * migrations show such clients exist <em>without</em> {@code internal.invoke} (wms / scm /
     * finance / erp). Its scopes are its own platform's, not this one's.
     */
    @Test
    @DisplayName("bite — 다른 플랫폼 워크로드 토큰(다른 scope) → 거절")
    void otherPlatformWorkloadToken_rejected() {
        OAuth2TokenValidatorResult r =
                chain().validate(token(ISSUER, "wms-internal-services-client", "wms.read"));

        assertThat(r.hasErrors()).isTrue();
    }

    @Test
    @DisplayName("대조군 — issuer 검증이 사슬에 남아 있다 (잘못된 issuer + 올바른 scope → 거절)")
    void wrongIssuer_stillRejected() {
        OAuth2TokenValidatorResult r =
                chain().validate(token("http://attacker", "auth-service-client", "internal.invoke"));

        assertThat(r.hasErrors())
                .as("the issuer/timestamp default validator is retained alongside the scope gate — "
                        + "this cell fails if the new chain replaced it instead of appending to it")
                .isTrue();
    }
}
