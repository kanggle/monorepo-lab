package com.wms.gateway.config;

import com.example.apigateway.config.SecurityConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.gateway.testsupport.JwksMockServer;
import com.wms.gateway.testsupport.JwtTestHelper;
import java.time.Duration;
import java.util.HashMap;
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
 * (TASK-MONO-696 AC-0).
 *
 * <p>Mirrors ecommerce's {@code SecurityConfigRealDecoderPathTest} (TASK-BE-595), minimally:
 *
 * <ul>
 *   <li>the real shared {@link SecurityConfig} filter chain that the wms gateway registers
 *       (entry point included),</li>
 *   <li>the real decoder from {@link OAuth2ResourceServerConfig#reactiveJwtDecoder()} — the
 *       shared validator chain with wms's tenant gate,</li>
 *   <li>a real RS256 signature checked against a JWKS document fetched over HTTP
 *       ({@link JwksMockServer}, an in-process MockWebServer).</li>
 * </ul>
 *
 * <p>No gateway routes, no Redis, no Docker; a probe controller stands in for the downstream.
 * Role admission ({@code RoleAdmissionFilter}) is a gateway filter and is deliberately not in
 * this context — the question here is only what the decoder accepts.
 *
 * <p>{@code application.yml} sets {@code spring.security.oauth2.resourceserver.jwt.audiences: wms},
 * but that property configures only Boot's auto-configured decoder, which backs off because this
 * service defines its own. <strong>The "passes today" cell is the one that flips in
 * TASK-MONO-696 AC-5 (phase 2, reject)</strong>: under the recorded decision (per-gateway
 * client-id allowlist, mismatch → 403) a token with no {@code aud} becomes 403.
 */
@SpringJUnitConfig(classes = {SecurityConfig.class, SecurityConfigRealDecoderPathTest.Beans.class})
@DisplayName("wms SecurityConfig — 실제 디코더 경로의 aud 처리 (TASK-MONO-696 AC-0)")
class SecurityConfigRealDecoderPathTest {

    private static final String PROTECTED = "/api/v1/master/probe";
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
    @DisplayName("(i) aud 없음 + tenant_id=wms → 통과 (오늘) — AC-5 phase 2 에서 403 으로 뒤집힌다")
    void noAudience_wmsTenant_passesToday_flipsInAc5() {
        // JwtTestHelper#signToken stamps tenant_id=wms and sets no aud.
        send(JWT.signToken("user-no-aud", null, 300L, Map.of()))
                .expectStatus().isOk()
                .expectBody(String.class).isEqualTo("reached");
    }

    @Test
    @DisplayName("(i) 대조군: 같은 토큰(aud 없음)에서 tenant_id 만 빼면 → 403 TENANT_FORBIDDEN")
    void noAudience_control_withoutTenant_is403() {
        // A null value drops the claim from the serialized payload (Nimbus omits null claims).
        Map<String, Object> withoutTenant = new HashMap<>();
        withoutTenant.put("tenant_id", null);
        send(JWT.signToken("user-no-aud", null, 300L, withoutTenant))
                .expectStatus().isForbidden()
                .expectBody().jsonPath("$.code").isEqualTo("TENANT_FORBIDDEN");
    }

    private WebTestClient.ResponseSpec send(String token) {
        return client.get().uri(PROTECTED)
                .header("Authorization", "Bearer " + token)
                .exchange();
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

        /** The production decoder, built by the production config class. */
        @Bean
        ReactiveJwtDecoder reactiveJwtDecoder() {
            return new OAuth2ResourceServerConfig(
                    JWKS.hostJwksUrl(), JwtTestHelper.SAS_ISSUER, JwtTestHelper.DEFAULT_TENANT_ID)
                    .reactiveJwtDecoder();
        }

        @Bean
        ProbeController probeController() {
            return new ProbeController();
        }
    }
}
