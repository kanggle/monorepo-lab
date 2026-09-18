package com.example.gateway.config;

import com.example.security.oauth2.AllowedAudiencesValidator;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.gateway.testsupport.JwksMockServer;
import com.example.gateway.testsupport.JwtTestHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jwt.ReactiveJwtDecoder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.config.EnableWebFlux;

/**
 * Phase 2 of the audience rollout — ENFORCE — through the <strong>real</strong> decoder path and
 * ecommerce's own {@link SecurityConfig} entry point (TASK-MONO-696 AC-5).
 *
 * <p><strong>This gateway does not ship in ENFORCE</strong> ({@code AudienceShippedConfigTest}
 * fails if it does). The mode is overridden for this test context only, so that switching phase
 * 2 on is a configuration flip whose behaviour has already been measured, not a first run in
 * production. Same harness as {@link SecurityConfigRealDecoderPathTest}: real filter chain, real
 * decoder from {@link OAuth2ResourceServerConfig}, real RS256 over a MockWebServer JWKS, no
 * Docker.
 */
@SpringJUnitConfig(classes = {SecurityConfig.class, SecurityConfigAudienceEnforceRealDecoderPathTest.Beans.class})
@DisplayName("SecurityConfig — ENFORCE 모드의 aud 거절 (TASK-MONO-696 phase 2, 테스트 한정 설정)")
class SecurityConfigAudienceEnforceRealDecoderPathTest {

    private static final String ISSUER = "https://test.local/issuer";
    private static final String PROTECTED = "/api/orders/123";
    private static final JwtTestHelper JWT = new JwtTestHelper();
    private static final JwksMockServer JWKS;

    static {
        try {
            JWKS = new JwksMockServer(JWT);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterAll
    static void stopJwks() throws Exception {
        JWKS.close();
    }

    @Autowired
    private ApplicationContext context;

    @Autowired
    private MeterRegistry registry;

    private WebTestClient client;

    @BeforeEach
    void bind() {
        client = WebTestClient.bindToApplicationContext(context).configureClient()
                .responseTimeout(Duration.ofSeconds(60))
                .build();
    }

    // -----------------------------------------------------------------------
    // Admitted
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("허용 aud (web-store client) → 200")
    void allowedAudience_is200() {
        send(token(List.of(JwtTestHelper.WEB_STORE_CLIENT_ID), "ecommerce"))
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("reached");
    }

    @Test
    @DisplayName("허용 aud (console client) → 200")
    void consoleAudience_is200() {
        send(token(List.of(JwtTestHelper.CONSOLE_CLIENT_ID), "ecommerce"))
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("aud 배열 [낯선 client, 허용 client] → 200 (교집합)")
    void arrayContainingAllowed_is200() {
        send(token(List.of("stranger-client", JwtTestHelper.WEB_STORE_CLIENT_ID), "ecommerce"))
                .expectStatus().isOk();
    }

    // -----------------------------------------------------------------------
    // Rejected for the audience → 403 AUDIENCE_FORBIDDEN (not 401)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("허용목록 밖 aud → 403 AUDIENCE_FORBIDDEN, UNAUTHORIZED 아님, 메트릭 audience_mismatch")
    void foreignAudience_is403AudienceForbidden() {
        double rejectedBefore = audience(AllowedAudiencesValidator.OUTCOME_MISMATCH_REJECTED);
        double reasonBefore = failure(GatewayMetrics.REASON_AUDIENCE_MISMATCH);
        double invalidBefore = failure("invalid");

        String body = send(token(List.of("wms"), "ecommerce"))
                .expectStatus().isForbidden()
                .expectBody(String.class).returnResult().getResponseBody();

        assertThat(body).contains("\"AUDIENCE_FORBIDDEN\"").doesNotContain("UNAUTHORIZED");
        assertThat(audience(AllowedAudiencesValidator.OUTCOME_MISMATCH_REJECTED) - rejectedBefore).isEqualTo(1.0);
        assertThat(failure(GatewayMetrics.REASON_AUDIENCE_MISMATCH) - reasonBefore).isEqualTo(1.0);
        assertThat(failure("invalid") - invalidBefore).isEqualTo(0.0);
    }

    @Test
    @DisplayName("aud 없음 → 403 AUDIENCE_FORBIDDEN")
    void missingAudience_is403() {
        send(JWT.signToken("user-no-aud", null, 300L, Map.of("tenant_id", "ecommerce")))
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("AUDIENCE_FORBIDDEN");
    }

    // -----------------------------------------------------------------------
    // Regression — non-audience rejections keep their status and code
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("회귀 금지 — audience 가 아닌 거절은 그대로")
    class NonAudienceRejectionsUnchanged {

        @Test
        @DisplayName("만료(허용 aud) → 401 UNAUTHORIZED")
        void expired_is401() {
            assertUnauthorized(send(expired(List.of(JwtTestHelper.WEB_STORE_CLIENT_ID))));
        }

        @Test
        @DisplayName("만료 + 낯선 aud → 401 (audience 는 유효한 토큰에만 평가된다)")
        void expiredAndForeignAudience_is401() {
            assertUnauthorized(send(expired(List.of("wms"))));
        }

        @Test
        @DisplayName("서명 불일치 → 401")
        void badSignature_is401() {
            String a = token(List.of(JwtTestHelper.WEB_STORE_CLIENT_ID), "ecommerce");
            String b = token(List.of(JwtTestHelper.WEB_STORE_CLIENT_ID), "ecommerce");
            String forged = a.substring(0, a.lastIndexOf('.')) + b.substring(b.lastIndexOf('.'));
            assertThat(forged).isNotEqualTo(a);
            assertUnauthorized(send(forged));
        }

        @Test
        @DisplayName("발급자 불일치 + 낯선 aud → 401 (403 아님)")
        void wrongIssuer_is401() {
            assertUnauthorized(send(JWT.signToken("user-x", null, 300L, Map.of(
                    "iss", "https://attacker.example.com",
                    "aud", List.of("wms"),
                    "tenant_id", "ecommerce"))));
        }

        @Test
        @DisplayName("토큰 부재 → 401")
        void missingToken_is401() {
            assertUnauthorized(client.get().uri(PROTECTED).exchange());
        }

        @Test
        @DisplayName("테넌트 불일치 + 낯선 aud → 403 TENANT_FORBIDDEN (AUDIENCE_FORBIDDEN 아님)")
        void tenantMismatch_isStillTenantForbidden() {
            double rejectedBefore = audience(AllowedAudiencesValidator.OUTCOME_MISMATCH_REJECTED);

            send(token(List.of("wms"), "globex"))
                    .expectStatus().isForbidden()
                    .expectBody().jsonPath("$.code").isEqualTo("TENANT_FORBIDDEN");

            assertThat(audience(AllowedAudiencesValidator.OUTCOME_MISMATCH_REJECTED) - rejectedBefore).isEqualTo(0.0);
        }
    }

    // -----------------------------------------------------------------------

    private static String token(List<String> aud, String tenantId) {
        return JWT.signToken("user-" + tenantId, null, 300L, Map.of("aud", aud, "tenant_id", tenantId));
    }

    /** Issued two hours ago, expired one hour ago (see SecurityConfigRealDecoderPathTest#expiredToken). */
    private static String expired(List<String> aud) {
        return JWT.signToken("user-expired", null, -3600L, Map.of(
                "iat", Date.from(Instant.now().minusSeconds(7200)),
                "aud", aud,
                "tenant_id", "ecommerce"));
    }

    private WebTestClient.ResponseSpec send(String token) {
        return client.get().uri(PROTECTED)
                .header("Authorization", "Bearer " + token)
                .exchange();
    }

    private static void assertUnauthorized(WebTestClient.ResponseSpec response) {
        response.expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    private double failure(String reason) {
        return registry.counter("gateway_jwt_validation_failure_total", "reason", reason).count();
    }

    private double audience(String outcome) {
        var counter = registry.find(AllowedAudiencesValidator.METRIC_NAME)
                .tag(AllowedAudiencesValidator.TAG_GATEWAY, OAuth2ResourceServerConfig.GATEWAY_NAME)
                .tag(AllowedAudiencesValidator.TAG_OUTCOME, outcome)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    @RestController
    static class ProbeController {
        @GetMapping("/api/orders/{id}")
        String order() {
            return "reached";
        }
    }

    @Configuration
    @EnableWebFlux
    static class Beans {
        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        GatewayMetrics gatewayMetrics(MeterRegistry registry) {
            return new GatewayMetrics(registry);
        }

        /** The production decoder, with the shipped allowlist and a TEST-ONLY ENFORCE mode. */
        @Bean
        ReactiveJwtDecoder reactiveJwtDecoder(MeterRegistry registry) {
            return new OAuth2ResourceServerConfig(JWKS.hostJwksUrl(), ISSUER, "ecommerce",
                    SecurityConfigRealDecoderPathTest.SHIPPED_ALLOWED_AUDIENCES, "ENFORCE", registry)
                    .reactiveJwtDecoder();
        }

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }
}
