package com.wms.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.apigateway.config.SecurityConfig;
import com.example.apigateway.security.AllowedAudiencesValidator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.gateway.testsupport.JwksMockServer;
import com.wms.gateway.testsupport.JwtTestHelper;
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
 * Phase 2 of the audience rollout — ENFORCE — through the real decoder path and the
 * <strong>shared</strong> {@code libs/java-gateway} {@link SecurityConfig} entry point, which is
 * the one wms, scm, erp, finance and fan all register (TASK-MONO-696 AC-5).
 *
 * <p><strong>wms does not ship in ENFORCE</strong> ({@code AudienceShippedConfigTest}). The mode
 * is overridden for this test context only.
 */
@SpringJUnitConfig(classes = {SecurityConfig.class, SecurityConfigAudienceEnforceRealDecoderPathTest.Beans.class})
@DisplayName("wms (공유 SecurityConfig) — ENFORCE 모드의 aud 거절 (TASK-MONO-696 phase 2, 테스트 한정 설정)")
class SecurityConfigAudienceEnforceRealDecoderPathTest {

    private static final String PROTECTED = SecurityConfigRealDecoderPathTest.PROTECTED;
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

    @Test
    @DisplayName("허용 aud (콘솔 client) → 200")
    void allowedAudience_is200() {
        send(JWT.signToken("user-console", null, 300L, Map.of()))
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("reached");
    }

    @Test
    @DisplayName("aud 배열 [낯선 client, 콘솔 client] → 200 (교집합)")
    void arrayContainingAllowed_is200() {
        send(JWT.signToken("user-multi", null, 300L,
                Map.of("aud", List.of("stranger-client", JwtTestHelper.CONSOLE_CLIENT_ID))))
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("허용목록 밖 aud → 403 AUDIENCE_FORBIDDEN (UNAUTHORIZED 아님), mismatch_rejected +1")
    void foreignAudience_is403AudienceForbidden() {
        double rejectedBefore = audience(AllowedAudiencesValidator.OUTCOME_MISMATCH_REJECTED);

        String body = send(JWT.signToken("user-foreign", null, 300L, Map.of("aud", List.of("wms-user-flow-client"))))
                .expectStatus().isForbidden()
                .expectBody(String.class).returnResult().getResponseBody();

        assertThat(body).contains("\"AUDIENCE_FORBIDDEN\"").doesNotContain("UNAUTHORIZED");
        assertThat(audience(AllowedAudiencesValidator.OUTCOME_MISMATCH_REJECTED) - rejectedBefore).isEqualTo(1.0);
    }

    @Test
    @DisplayName("aud 없음 → 403 AUDIENCE_FORBIDDEN")
    void missingAudience_is403() {
        send(JWT.signToken("user-no-aud", null, 300L, SecurityConfigRealDecoderPathTest.without("aud")))
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("AUDIENCE_FORBIDDEN");
    }

    @Nested
    @DisplayName("회귀 금지 — audience 가 아닌 거절은 그대로")
    class NonAudienceRejectionsUnchanged {

        @Test
        @DisplayName("만료 + 낯선 aud → 401 UNAUTHORIZED")
        void expired_is401() {
            assertUnauthorized(send(JWT.signToken("user-expired", null, -3600L, Map.of(
                    "iat", Date.from(Instant.now().minusSeconds(7200)),
                    "aud", List.of("stranger-client")))));
        }

        @Test
        @DisplayName("서명 불일치 → 401")
        void badSignature_is401() {
            assertUnauthorized(send(JWT.signForgedSignatureToken("user-forged")));
        }

        @Test
        @DisplayName("허용되지 않은 발급자 + 낯선 aud → 401 (403 아님)")
        void wrongIssuer_is401() {
            assertUnauthorized(send(JWT.signToken("https://attacker.example.com", "user-x", null, 300L,
                    Map.of("aud", List.of("stranger-client")))));
        }

        @Test
        @DisplayName("토큰 부재 → 401")
        void missingToken_is401() {
            assertUnauthorized(client.get().uri(PROTECTED).exchange());
        }

        @Test
        @DisplayName("테넌트 불일치 + 낯선 aud → 403 TENANT_FORBIDDEN (AUDIENCE_FORBIDDEN 아님)")
        void tenantMismatch_isStillTenantForbidden() {
            send(JWT.signToken("user-scm", null, 300L,
                    Map.of("tenant_id", "scm", "aud", List.of("stranger-client"))))
                    .expectStatus().isForbidden()
                    .expectBody().jsonPath("$.code").isEqualTo("TENANT_FORBIDDEN");
        }
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

    private double audience(String outcome) {
        var counter = registry.find(AllowedAudiencesValidator.METRIC_NAME)
                .tag(AllowedAudiencesValidator.TAG_GATEWAY, OAuth2ResourceServerConfig.GATEWAY_NAME)
                .tag(AllowedAudiencesValidator.TAG_OUTCOME, outcome)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }

    @RestController
    static class ProbeController {
        @GetMapping(SecurityConfigRealDecoderPathTest.PROTECTED)
        String probe() {
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

        /** The production decoder, with the shipped allowlist and a TEST-ONLY ENFORCE mode. */
        @Bean
        ReactiveJwtDecoder reactiveJwtDecoder(MeterRegistry registry) {
            return new OAuth2ResourceServerConfig(
                    JWKS.hostJwksUrl(), JwtTestHelper.SAS_ISSUER, JwtTestHelper.DEFAULT_TENANT_ID,
                    SecurityConfigRealDecoderPathTest.SHIPPED_ALLOWED_AUDIENCES, "ENFORCE", registry)
                    .reactiveJwtDecoder();
        }

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }
}
