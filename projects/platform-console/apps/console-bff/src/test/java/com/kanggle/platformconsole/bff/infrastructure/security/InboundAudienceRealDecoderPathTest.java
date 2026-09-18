package com.kanggle.platformconsole.bff.infrastructure.security;

import com.example.security.oauth2.AllowedAudiencesValidator;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The rule-5 check on the <strong>real</strong> decoder — {@code TASK-MONO-712} AC-2.
 *
 * <p>Every token here is signed by a key this test generated and served over a real JWKS endpoint,
 * and decoded by the very {@link JwtDecoder} bean {@link InboundAudienceConfig} builds. That is
 * deliberate: asserting the validator in isolation would prove the validator works while saying
 * nothing about whether console-bff <em>installed</em> it — and "the check exists but is not
 * wired" is the failure this whole ticket is about.
 *
 * <p>The status cells go through {@link SecurityConfig#onAuthenticationFailure} with the exception
 * the decoder actually threw, rather than a hand-built one, so the cause-chain walk is exercised
 * on real input.
 */
@DisplayName("console-bff — 실제 디코더 경로의 audience 거절 (TASK-MONO-712 AC-2)")
class InboundAudienceRealDecoderPathTest {

    private static final String ISSUER = "http://test-issuer";
    private static final String ALLOWED = "platform-console-web";

    private static MockWebServer jwksServer;
    private static RSAKey signingKey;
    private static JwtDecoder decoder;

    @BeforeAll
    static void startEdge() throws Exception {
        signingKey = new RSAKeyGenerator(2048).keyID("it-key").generate();
        jwksServer = new MockWebServer();
        jwksServer.setDispatcher(new Dispatcher() {
            @Override
            public MockResponse dispatch(RecordedRequest request) {
                return new MockResponse()
                        .setResponseCode(200)
                        .setHeader("Content-Type", "application/json")
                        .setBody("{\"keys\":[" + signingKey.toPublicJWK().toJSONString() + "]}");
            }
        });
        jwksServer.start();

        InboundAudienceConfig config = new InboundAudienceConfig(
                jwksServer.url("/oauth2/jwks").toString(),
                ISSUER,
                List.of(ALLOWED),
                new SimpleMeterRegistry());
        decoder = config.jwtDecoder(config.consoleBffAudienceValidator());
    }

    @AfterAll
    static void stopEdge() throws Exception {
        jwksServer.shutdown();
    }

    // ── admitted ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("대조군 — allowlist 안의 aud 는 통과한다")
    void allowedAudience_decodes() {
        assertThat(decoder.decode(token(List.of(ALLOWED))).getAudience()).containsExactly(ALLOWED);
    }

    /**
     * 🔵 {@code aud} is a set, and rule 5 is an <em>intersection</em>, not equality. A token that
     * also names other relying parties is still addressed to this one.
     */
    @Test
    @DisplayName("대조군 — aud 배열 중 하나만 일치해도 통과한다 (교집합이지 일치가 아니다)")
    void audienceArrayWithOneMatch_decodes() {
        assertThat(decoder.decode(token(List.of("some-other-client", ALLOWED))).getAudience())
                .contains(ALLOWED);
    }

    // ── rejected ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("allowlist 밖의 aud 는 audience_mismatch 로 거절된다")
    void foreignAudience_rejected() {
        assertThatThrownBy(() -> decoder.decode(token(List.of("some-other-client"))))
                .isInstanceOf(JwtValidationException.class)
                .satisfies(e -> assertThat(errorCodes((JwtValidationException) e))
                        .contains(AllowedAudiencesValidator.ERROR_CODE_AUDIENCE_MISMATCH));
    }

    /**
     * A token with no {@code aud} is the empty set; the empty set intersects nothing. 🔴 This cell
     * is the one that would quietly invert if the check were ever moved back onto Boot's property
     * with an empty list — there, "no audiences configured" means "no validator", and this token
     * would be admitted.
     */
    @Test
    @DisplayName("aud 가 없는 토큰도 거절된다 (빈 집합은 아무것과도 교집합이 없다)")
    void missingAudience_rejected() {
        assertThatThrownBy(() -> decoder.decode(token(null)))
                .isInstanceOf(JwtValidationException.class)
                .satisfies(e -> assertThat(errorCodes((JwtValidationException) e))
                        .contains(AllowedAudiencesValidator.ERROR_CODE_AUDIENCE_MISMATCH));
    }

    // ── the status the client actually sees ─────────────────────────────────

    @Test
    @DisplayName("audience 거절은 403 PERMISSION_DENIED 로 나간다 — 401 이 아니다")
    void audienceRejection_isForbidden() throws Exception {
        MockHttpServletResponse response = renderFailure(token(List.of("some-other-client")));

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("PERMISSION_DENIED");
    }

    /**
     * 🔴 The control that stops this from being a blanket 403. If every decoder failure became 403,
     * an expired or forged token would stop telling the console to refresh — the opposite defect,
     * and invisible from the cell above alone.
     */
    @Test
    @DisplayName("대조군 — 서명이 틀린 토큰은 여전히 401 이다 (403 이 전부를 삼키지 않는다)")
    void otherFailures_stay401() throws Exception {
        MockHttpServletResponse response = renderFailure("not.a.valid.jwt");

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("UNAUTHORIZED");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Drives the decoder, then renders whatever it threw through the real entry point. */
    private MockHttpServletResponse renderFailure(String tokenValue) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        try {
            decoder.decode(tokenValue);
            throw new AssertionError("expected the decoder to reject this token, but it decoded");
        } catch (Exception decodeFailure) {
            SecurityConfig.onAuthenticationFailure(
                    null, response, new InvalidBearerTokenException("rejected", decodeFailure));
        }
        return response;
    }

    private static List<String> errorCodes(JwtValidationException e) {
        return e.getErrors().stream().map(OAuth2Error::getErrorCode).toList();
    }

    /** A correctly signed, unexpired token whose only variable is {@code aud}. */
    private static String token(List<String> audience) {
        try {
            JWTClaimsSet.Builder claims = new JWTClaimsSet.Builder()
                    .issuer(ISSUER)
                    .subject("op-user-712")
                    .expirationTime(new Date(System.currentTimeMillis() + 3_600_000));
            if (audience != null) {
                claims.audience(audience);
            }
            SignedJWT signed = new SignedJWT(
                    new JWSHeader.Builder(JWSAlgorithm.RS256).keyID(signingKey.getKeyID()).build(),
                    claims.build());
            signed.sign(new RSASSASigner(signingKey));
            return signed.serialize();
        } catch (Exception e) {
            throw new IllegalStateException("token minting failed", e);
        }
    }
}
