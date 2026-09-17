package com.example.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.apigateway.security.AllowedAudiencesValidator;
import com.example.gateway.testsupport.JwksMockServer;
import com.example.gateway.testsupport.JwtTestHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Tenant rejection status, measured through the <strong>real</strong> decoder path
 * (TASK-BE-595).
 *
 * <p>{@link SecurityConfigTenantErrorMappingTest} calls the entry point with an exception it
 * builds itself — and the one it built for TASK-BE-501 had a shape Spring never produces, so
 * that suite was green while production answered every tenant rejection with 401. This suite
 * does not build the exception. It runs the production pieces end to end and lets Spring
 * build it:
 *
 * <ul>
 *   <li>the real {@link SecurityConfig} filter chain (entry point included),</li>
 *   <li>the real decoder from {@link OAuth2ResourceServerConfig#reactiveJwtDecoder()} — the
 *       shared validator chain with ecommerce's tenant gate,</li>
 *   <li>a real RS256 signature checked against a JWKS document fetched over HTTP
 *       ({@link JwksMockServer}, an in-process MockWebServer).</li>
 * </ul>
 *
 * <p><strong>Docker-free by construction</strong> — no Redis, no gateway routes; a probe
 * controller stands in for the downstream. {@code GatewayIntegrationTest} asserts the same
 * statuses on a booted gateway, but needs Docker, so without this suite a host without Docker
 * could not tell the defect from the fix.
 */
@SpringJUnitConfig(classes = {SecurityConfig.class, SecurityConfigRealDecoderPathTest.Beans.class})
@DisplayName("SecurityConfig — 실제 디코더 경로의 테넌트 거절 보고 (TASK-BE-595)")
class SecurityConfigRealDecoderPathTest {

    private static final String ISSUER = "https://test.local/issuer";
    private static final String PROTECTED = "/api/orders/123";
    /** Kept equal to application.yml by {@code AudienceShippedConfigTest}. */
    static final String SHIPPED_ALLOWED_AUDIENCES = "platform-console-web,ecommerce-web-store-client";
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
        // Whichever test runs first pays for context start-up and the first JWKS fetch; the
        // 5s default timed out on exactly that request (measured 12.7s on this host).
        client = WebTestClient.bindToApplicationContext(context).configureClient()
                .responseTimeout(Duration.ofSeconds(60))
                .build();
    }

    // -----------------------------------------------------------------------
    // Controls — prove the requests really reach the tenant gate (AC-0 injection check)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("대조군: 같은 모양의 토큰에서 tenant_id 만 ecommerce 로 맞추면 통과한다")
    void control_matchingTenant_passes() {
        send(JWT.signTokenWithIssuerAndTenant(ISSUER, "ecommerce"))
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("reached");
    }

    @Test
    @DisplayName("대조군: 외부 테넌트라도 entitled_domains ∋ ecommerce 면 통과한다")
    void control_entitledForeignTenant_passes() {
        send(JWT.signTokenWithIssuerTenantAndEntitlements(ISSUER, "globex", List.of("ecommerce")))
                .expectStatus().isOk();
    }

    // -----------------------------------------------------------------------
    // AC-1 — tenant rejection is 403 TENANT_FORBIDDEN
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("엔타이틀먼트 없는 외부 테넌트 → 403 TENANT_FORBIDDEN, UNAUTHORIZED 아님, 메트릭은 tenant_mismatch")
    void unentitledForeignTenant_is403TenantForbidden() {
        double tenantBefore = counter(GatewayMetrics.REASON_TENANT_MISMATCH);
        double invalidBefore = counter("invalid");

        String body = send(JWT.signTokenWithIssuerAndTenant(ISSUER, "globex"))
                .expectStatus().isForbidden()
                .expectBody(String.class).returnResult().getResponseBody();

        assertThat(body).contains("\"TENANT_FORBIDDEN\"").doesNotContain("UNAUTHORIZED");
        assertThat(counter(GatewayMetrics.REASON_TENANT_MISMATCH) - tenantBefore).isEqualTo(1.0);
        assertThat(counter("invalid") - invalidBefore).isEqualTo(0.0);
    }

    @Test
    @DisplayName("다른 도메인에만 구독된 테넌트(wms, entitled=[wms]) → 403 TENANT_FORBIDDEN")
    void tenantEntitledElsewhere_is403() {
        send(JWT.signTokenWithIssuerTenantAndEntitlements(ISSUER, "wms", List.of("wms")))
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("TENANT_FORBIDDEN");
    }

    @Test
    @DisplayName("tenant_id 부재 → 403 TENANT_FORBIDDEN (tenant_mismatch 계열)")
    void missingTenant_is403() {
        send(JWT.signTokenWithIssuerAndTenant(ISSUER, null))
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("TENANT_FORBIDDEN");
    }

    @Test
    @DisplayName("만료 + 외부 테넌트 → 403 (재인증으로 테넌트는 고쳐지지 않는다 — 의도된 선택)")
    void expiredAndForeignTenant_is403() {
        send(expiredToken("globex"))
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("TENANT_FORBIDDEN");
    }

    // -----------------------------------------------------------------------
    // AC-1 regression — everything that is NOT a tenant rejection stays 401
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("회귀 금지 — 테넌트 문제가 아닌 거절은 여전히 401")
    class StillUnauthorized {

        @Test
        @DisplayName("만료 토큰(테넌트는 맞음) → 401 UNAUTHORIZED, 메트릭은 invalid")
        void expired_is401() {
            double tenantBefore = counter(GatewayMetrics.REASON_TENANT_MISMATCH);
            double invalidBefore = counter("invalid");

            assertUnauthorized(send(expiredToken("ecommerce")));

            assertThat(counter("invalid") - invalidBefore).isEqualTo(1.0);
            assertThat(counter(GatewayMetrics.REASON_TENANT_MISMATCH) - tenantBefore).isEqualTo(0.0);
        }

        @Test
        @DisplayName("서명 불일치(같은 kid, 다른 서명) → 401")
        void badSignature_is401() {
            String a = JWT.signTokenWithIssuerAndTenant(ISSUER, "ecommerce");
            String b = JWT.signTokenWithIssuerAndTenant(ISSUER, "ecommerce");
            // header.payload of A with the signature of B: same key id, signature does not verify.
            String forged = a.substring(0, a.lastIndexOf('.')) + b.substring(b.lastIndexOf('.'));
            assertThat(forged).isNotEqualTo(a);

            assertUnauthorized(send(forged));
        }

        @Test
        @DisplayName("발급자 불일치(테넌트는 맞음) → 401")
        void wrongIssuer_is401() {
            assertUnauthorized(send(JWT.signTokenWithIssuerAndTenant("https://attacker.example.com", "ecommerce")));
        }

        @Test
        @DisplayName("토큰 부재 → 401")
        void missingToken_is401() {
            assertUnauthorized(client.get().uri(PROTECTED).exchange());
        }
    }

    // -----------------------------------------------------------------------
    // TASK-MONO-696 AC-5 — phase 1 (SHADOW): audience is checked, recorded, not rejected
    // -----------------------------------------------------------------------

    /**
     * Phase 1 of the audience rollout, on the real decoder path (TASK-MONO-696 AC-5).
     *
     * <p>These were the AC-0 "passes today" cells: before the shared chain carried an audience
     * gate, {@code aud} was never looked at. They flip <em>in what they assert</em>, not in
     * status: in shadow mode a missing or foreign {@code aud} still reaches the route, and the
     * mismatch is now <strong>counted</strong> ({@code gateway.jwt.audience}, outcome
     * {@code mismatch_shadowed}). A "passes" assertion alone could not tell shadow mode from no
     * check at all — the counter is what distinguishes them. Phase 2 (reject) is asserted in
     * {@link SecurityConfigAudienceEnforceRealDecoderPathTest}.
     *
     * <p>The controls stay: the same claims minus {@code tenant_id} is refused with 403, which
     * proves "reached" means the real decoder accepted the token — and, since the audience gate
     * only runs on a token the rest of the chain accepted, those controls must count nothing.
     */
    @Nested
    @DisplayName("TASK-MONO-696 AC-5 — phase 1 SHADOW: aud 불일치는 통과하고 기록된다")
    class AudienceShadowed {

        @Test
        @DisplayName("(i) aud 없음 + tenant_id=ecommerce → 200 + mismatch_shadowed +1")
        void noAudience_ecommerceTenant_passesInShadow_andIsCounted() {
            double shadowedBefore = audience(AllowedAudiencesValidator.OUTCOME_MISMATCH_SHADOWED);
            double matchBefore = audience(AllowedAudiencesValidator.OUTCOME_MATCH);

            send(JWT.signToken("user-no-aud", null, 300L, Map.of("tenant_id", "ecommerce")))
                    .expectStatus().isOk()
                    .expectBody(String.class).isEqualTo("reached");

            assertThat(audience(AllowedAudiencesValidator.OUTCOME_MISMATCH_SHADOWED) - shadowedBefore).isEqualTo(1.0);
            assertThat(audience(AllowedAudiencesValidator.OUTCOME_MATCH) - matchBefore).isEqualTo(0.0);
        }

        @Test
        @DisplayName("(i) 대조군: 같은 토큰(aud 없음)에서 tenant_id 만 빼면 → 403 TENANT_FORBIDDEN, audience 는 세지 않음")
        void noAudience_control_withoutTenant_is403() {
            double shadowedBefore = audience(AllowedAudiencesValidator.OUTCOME_MISMATCH_SHADOWED);

            send(JWT.signToken("user-no-aud", null, 300L, Map.of()))
                    .expectStatus().isForbidden()
                    .expectBody().jsonPath("$.code").isEqualTo("TENANT_FORBIDDEN");

            assertThat(audience(AllowedAudiencesValidator.OUTCOME_MISMATCH_SHADOWED) - shadowedBefore).isEqualTo(0.0);
        }

        @Test
        @DisplayName("(ii) aud=[wms] + tenant_id=ecommerce → 200 + mismatch_shadowed +1")
        void foreignAudience_ecommerceTenant_passesInShadow_andIsCounted() {
            double shadowedBefore = audience(AllowedAudiencesValidator.OUTCOME_MISMATCH_SHADOWED);

            send(JWT.signToken("user-wms-aud", null, 300L,
                    Map.of("aud", List.of("wms"), "tenant_id", "ecommerce")))
                    .expectStatus().isOk()
                    .expectBody(String.class).isEqualTo("reached");

            assertThat(audience(AllowedAudiencesValidator.OUTCOME_MISMATCH_SHADOWED) - shadowedBefore).isEqualTo(1.0);
        }

        @Test
        @DisplayName("(ii) 대조군: 같은 토큰(aud=[wms])에서 tenant_id 만 빼면 → 403 TENANT_FORBIDDEN")
        void foreignAudience_control_withoutTenant_is403() {
            send(JWT.signToken("user-wms-aud", null, 300L, Map.of("aud", List.of("wms"))))
                    .expectStatus().isForbidden()
                    .expectBody().jsonPath("$.code").isEqualTo("TENANT_FORBIDDEN");
        }

        @Test
        @DisplayName("(iii) 허용 aud(web-store client) → 200 + match +1, mismatch 0")
        void allowedAudience_passes_andCountsMatch() {
            double shadowedBefore = audience(AllowedAudiencesValidator.OUTCOME_MISMATCH_SHADOWED);
            double matchBefore = audience(AllowedAudiencesValidator.OUTCOME_MATCH);

            send(JWT.signTokenWithIssuerAndTenant(ISSUER, "ecommerce"))
                    .expectStatus().isOk();

            assertThat(audience(AllowedAudiencesValidator.OUTCOME_MATCH) - matchBefore).isEqualTo(1.0);
            assertThat(audience(AllowedAudiencesValidator.OUTCOME_MISMATCH_SHADOWED) - shadowedBefore).isEqualTo(0.0);
        }
    }

    // -----------------------------------------------------------------------

    private WebTestClient.ResponseSpec send(String token) {
        return client.get().uri(PROTECTED)
                .header("Authorization", "Bearer " + token)
                .exchange();
    }

    private static void assertUnauthorized(WebTestClient.ResponseSpec response) {
        response.expectStatus().isUnauthorized()
                .expectBody().jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    /**
     * Issued two hours ago, expired one hour ago — past JwtTimestampValidator's 60s skew.
     * <p>The {@code iat} override matters: the helper stamps {@code iat=now}, and a token whose
     * {@code exp} precedes its {@code iat} is thrown out by Spring's {@code Jwt} builder as
     * malformed ({@code BadJwtException}) before any validator runs — it would be a 401 for the
     * wrong reason and never exercise expiry (measured while writing this suite).
     */
    private static String expiredToken(String tenantId) {
        return JWT.signToken("user-expired", null, -3600L, Map.of(
                "iat", Date.from(Instant.now().minusSeconds(7200)),
                "aud", List.of(JwtTestHelper.WEB_STORE_CLIENT_ID),
                "account_type", "CONSUMER",
                "tenant_id", tenantId));
    }

    private double counter(String reason) {
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

        /**
         * The production decoder, built by the production config class — with the allowlist and
         * mode this gateway ships (SHADOW), so this suite measures phase 1 as deployed.
         */
        @Bean
        ReactiveJwtDecoder reactiveJwtDecoder(MeterRegistry registry) {
            return new OAuth2ResourceServerConfig(JWKS.hostJwksUrl(), ISSUER, "ecommerce",
                    SHIPPED_ALLOWED_AUDIENCES, "SHADOW", registry)
                    .reactiveJwtDecoder();
        }

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }
}
