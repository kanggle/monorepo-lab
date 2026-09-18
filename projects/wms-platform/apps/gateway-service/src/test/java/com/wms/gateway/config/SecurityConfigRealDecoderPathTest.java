package com.wms.gateway.config;

import com.example.security.oauth2.AllowedAudiencesValidator;
import static org.assertj.core.api.Assertions.assertThat;

import com.example.apigateway.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.gateway.testsupport.JwksMockServer;
import com.wms.gateway.testsupport.JwtTestHelper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
 * Audience handling at the wms edge, measured through the <strong>real</strong> decoder path
 * (TASK-MONO-696 AC-0 → AC-5, phase 1).
 *
 * <p>Mirrors ecommerce's {@code SecurityConfigRealDecoderPathTest} (TASK-BE-595), minimally:
 *
 * <ul>
 *   <li>the real shared {@link SecurityConfig} filter chain that the wms gateway registers
 *       (entry point included),</li>
 *   <li>the real decoder from {@link OAuth2ResourceServerConfig#reactiveJwtDecoder()} — the
 *       shared validator chain with wms's tenant gate and audience gate, configured with the
 *       allowlist and mode this gateway ships (SHADOW),</li>
 *   <li>a real RS256 signature checked against a JWKS document fetched over HTTP
 *       ({@link JwksMockServer}, an in-process MockWebServer).</li>
 * </ul>
 *
 * <p>No gateway routes, no Redis, no Docker; a probe controller stands in for the downstream.
 * Role admission ({@code RoleAdmissionFilter}) is a gateway filter and is deliberately not in
 * this context — the question here is only what the decoder accepts.
 *
 * <p><strong>AC-0 recorded that {@code aud} was never checked</strong>: the
 * {@code spring.security.oauth2.resourceserver.jwt.audiences: wms} property configured a decoder
 * this service replaces (the property is now deleted). In phase 1 the no-{@code aud} token still
 * reaches the route — and the mismatch is now counted, which is the assertion that tells shadow
 * mode apart from no check at all. Phase 2 (reject) is {@link SecurityConfigAudienceEnforceRealDecoderPathTest}.
 */
@SpringJUnitConfig(classes = {SecurityConfig.class, SecurityConfigRealDecoderPathTest.Beans.class})
@DisplayName("wms SecurityConfig — 실제 디코더 경로의 aud 처리 (TASK-MONO-696 phase 1 SHADOW)")
class SecurityConfigRealDecoderPathTest {

    static final String PROTECTED = "/api/v1/master/probe";
    /** Kept equal to application.yml by {@code AudienceShippedConfigTest}. */
    static final String SHIPPED_ALLOWED_AUDIENCES = "platform-console-web";
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
        // First request pays for context start-up and the first JWKS fetch (ecommerce measured
        // 12.7s for the same shape; the 5s default timed out there).
        client = WebTestClient.bindToApplicationContext(context).configureClient()
                .responseTimeout(Duration.ofSeconds(60))
                .build();
    }

    @Test
    @DisplayName("(i) aud 없음 + tenant_id=wms → 200 + mismatch_shadowed +1 (phase 1 — 거절하지 않고 센다)")
    void noAudience_wmsTenant_passesInShadow_andIsCounted() {
        double shadowedBefore = audience(AllowedAudiencesValidator.OUTCOME_MISMATCH_SHADOWED);

        send(JWT.signToken("user-no-aud", null, 300L, without("aud")))
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("reached");

        assertThat(audience(AllowedAudiencesValidator.OUTCOME_MISMATCH_SHADOWED) - shadowedBefore).isEqualTo(1.0);
    }

    @Test
    @DisplayName("(i) 대조군: 같은 토큰(aud 없음)에서 tenant_id 만 빼면 → 403 TENANT_FORBIDDEN, audience 는 세지 않음")
    void noAudience_control_withoutTenant_is403() {
        double shadowedBefore = audience(AllowedAudiencesValidator.OUTCOME_MISMATCH_SHADOWED);
        Map<String, Object> claims = without("aud");
        // A null value drops the claim from the serialized payload (Nimbus omits null claims).
        claims.put("tenant_id", null);

        send(JWT.signToken("user-no-aud", null, 300L, claims))
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("TENANT_FORBIDDEN");

        assertThat(audience(AllowedAudiencesValidator.OUTCOME_MISMATCH_SHADOWED) - shadowedBefore).isEqualTo(0.0);
    }

    @Test
    @DisplayName("(ii) 낯선 aud + tenant_id=wms → 200 + mismatch_shadowed +1")
    void foreignAudience_passesInShadow_andIsCounted() {
        double shadowedBefore = audience(AllowedAudiencesValidator.OUTCOME_MISMATCH_SHADOWED);

        send(JWT.signToken("user-foreign-aud", null, 300L, Map.of("aud", List.of("wms-user-flow-client"))))
                .expectStatus().isOk();

        assertThat(audience(AllowedAudiencesValidator.OUTCOME_MISMATCH_SHADOWED) - shadowedBefore).isEqualTo(1.0);
    }

    @Test
    @DisplayName("(iii) 헬퍼 기본 aud(콘솔 client) → 200 + match +1, mismatch 0")
    void consoleAudience_passes_andCountsMatch() {
        double shadowedBefore = audience(AllowedAudiencesValidator.OUTCOME_MISMATCH_SHADOWED);
        double matchBefore = audience(AllowedAudiencesValidator.OUTCOME_MATCH);

        send(JWT.signToken("user-console", null, 300L, Map.of()))
                .expectStatus().isOk();

        assertThat(audience(AllowedAudiencesValidator.OUTCOME_MATCH) - matchBefore).isEqualTo(1.0);
        assertThat(audience(AllowedAudiencesValidator.OUTCOME_MISMATCH_SHADOWED) - shadowedBefore).isEqualTo(0.0);
    }

    static Map<String, Object> without(String claim) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(claim, null);
        return claims;
    }

    private WebTestClient.ResponseSpec send(String token) {
        return client.get().uri(PROTECTED)
                .header("Authorization", "Bearer " + token)
                .exchange();
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
        @GetMapping(PROTECTED)
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

        /** The production decoder, built by the production config class, as shipped (SHADOW). */
        @Bean
        ReactiveJwtDecoder reactiveJwtDecoder(MeterRegistry registry) {
            return new OAuth2ResourceServerConfig(
                    JWKS.hostJwksUrl(), JwtTestHelper.SAS_ISSUER, JwtTestHelper.DEFAULT_TENANT_ID,
                    SHIPPED_ALLOWED_AUDIENCES, "SHADOW", registry)
                    .reactiveJwtDecoder();
        }

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }
}
