package com.example.gateway;

import com.example.gateway.testsupport.JwksMockServer;
import com.example.gateway.testsupport.JwtTestHelper;
import com.redis.testcontainers.RedisContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("Gateway 통합 테스트 (RSA/JWKS)")
class GatewayIntegrationTest {

    private static final JwtTestHelper jwtHelper = new JwtTestHelper();
    private static final JwksMockServer jwksMockServer;

    static {
        try {
            jwksMockServer = new JwksMockServer(jwtHelper);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @AfterAll
    static void stopJwksMockServer() throws Exception {
        jwksMockServer.close();
    }

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri",
                jwksMockServer::hostJwksUrl);
    }

    @Autowired
    private WebTestClient webTestClient;

    // -----------------------------------------------------------------------
    // 401 scenarios
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("인증 토큰 없이 보호된 경로 요청 시 401을 반환한다")
    void protectedRoute_noToken_returns401() {
        webTestClient.get()
                .uri("/api/orders/123")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED")
                .jsonPath("$.message").isNotEmpty()
                .jsonPath("$.timestamp").isNotEmpty();
    }

    @Test
    @DisplayName("잘못된 JWT로 보호된 경로 요청 시 401을 반환한다")
    void protectedRoute_invalidToken_returns401() {
        webTestClient.get()
                .uri("/api/orders/123")
                .header("Authorization", "Bearer invalid.token.value")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("만료된 JWT로 보호된 경로 요청 시 401을 반환한다")
    void protectedRoute_expiredToken_returns401() {
        // signToken with -1 second TTL → already expired
        String expiredToken = jwtHelper.signToken(
                "user-123", null, -1L,
                java.util.Map.of(
                        "aud", List.of("ecommerce"),
                        "account_type", "CONSUMER",
                        "email", "user@example.com"));

        webTestClient.get()
                .uri("/api/orders/123")
                .header("Authorization", "Bearer " + expiredToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("audience가 일치하지 않는 JWT로 보호된 경로 요청 시 401을 반환한다")
    void protectedRoute_wrongAudience_returns401() {
        // No aud claim → Spring Security rejects it when audiences is configured
        String noAudToken = jwtHelper.signToken(
                "user-123", "BUYER", 300L,
                java.util.Map.of("account_type", "CONSUMER", "email", "user@example.com"));

        webTestClient.get()
                .uri("/api/orders/123")
                .header("Authorization", "Bearer " + noAudToken)
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // -----------------------------------------------------------------------
    // Valid CONSUMER token scenarios
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("유효한 CONSUMER JWT로 보호된 경로 요청 시 JWT 필터를 통과한다 (다운스트림 불가 → 5xx)")
    void protectedRoute_validConsumerToken_passesJwtFilter() {
        String token = jwtHelper.signConsumerToken("user-123", List.of("BUYER"));

        webTestClient.get()
                .uri("/api/orders/123")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }

    @Test
    @DisplayName("CONSUMER 토큰으로 /api/admin/ 경로 접근 시 403을 반환한다")
    void adminRoute_consumerToken_returns403() {
        String token = jwtHelper.signConsumerToken("user-123", List.of("BUYER"));

        webTestClient.get()
                .uri("/api/admin/products/42")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("FORBIDDEN");
    }

    // -----------------------------------------------------------------------
    // Spoofed identity header scenarios
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("보호된 경로에 스푸핑된 X-User-Id 헤더가 전달되어도 토큰 없으면 401을 반환한다")
    void protectedRoute_spoofedHeaders_noToken_returns401() {
        webTestClient.get()
                .uri("/api/orders/123")
                .header("X-User-Id", "spoofed-user")
                .header("X-User-Email", "spoofed@evil.com")
                .header("X-User-Role", "ADMIN")
                .exchange()
                .expectStatus().isUnauthorized();
    }

    // -----------------------------------------------------------------------
    // Public route scenarios
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("공개 경로 POST /api/auth/login은 토큰 없이 JWT 필터를 통과한다 (다운스트림 불가 → 5xx)")
    void publicRoute_login_passesWithoutToken() {
        webTestClient.post()
                .uri("/api/auth/login")
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }

    @Test
    @DisplayName("공개 경로 POST /api/auth/signup은 토큰 없이 JWT 필터를 통과한다")
    void publicRoute_signup_passesWithoutToken() {
        webTestClient.post()
                .uri("/api/auth/signup")
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }

    @Test
    @DisplayName("공개 경로 POST /api/auth/refresh는 토큰 없이 JWT 필터를 통과한다")
    void publicRoute_refresh_passesWithoutToken() {
        webTestClient.post()
                .uri("/api/auth/refresh")
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }

    @Test
    @DisplayName("공개 경로 GET /api/products/**는 토큰 없이 JWT 필터를 통과한다 (다운스트림 불가 → 5xx)")
    void publicRoute_products_passesWithoutToken() {
        webTestClient.get()
                .uri("/api/products/42")
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }

    @Test
    @DisplayName("공개 경로 GET /api/search/**는 토큰 없이 JWT 필터를 통과한다")
    void publicRoute_search_passesWithoutToken() {
        webTestClient.get()
                .uri("/api/search/products?q=shoes")
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }

    @Test
    @DisplayName("/actuator/health 는 인증 없이 UP 상태를 반환한다")
    void actuatorHealth_returnsUp() {
        webTestClient.get()
                .uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    // -----------------------------------------------------------------------
    // TASK-MONO-027 — TenantClaimValidator + AllowedIssuersValidator scenarios
    //
    // The integration profile (application-integration-test.yml) configures:
    //   ecommerce.oauth2.allowed-issuers = https://test.local/issuer,
    //                                      global-account-platform
    //   ecommerce.oauth2.required-tenant-id = ecommerce
    //
    // Spring Security WebFlux surfaces all JWT validation failures
    // (issuer mismatch, tenant_id mismatch, missing claim) as 401 via
    // ServerAuthenticationEntryPoint — not 403 — because the token is
    // rejected before authentication completes.
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("tenant_id=ecommerce + iss=test.local → JWT 필터 통과 (다운스트림 불가 → 5xx)")
    void protectedRoute_ecommerceTenant_passesJwtFilter() {
        String token = jwtHelper.signTokenWithIssuerAndTenant(
                "https://test.local/issuer", "ecommerce");

        webTestClient.get()
                .uri("/api/orders/123")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }

    @Test
    @DisplayName("tenant_id=wms (cross-tenant) → 401")
    void protectedRoute_crossTenantWms_returns401() {
        String token = jwtHelper.signTokenWithIssuerAndTenant(
                "https://test.local/issuer", "wms");

        webTestClient.get()
                .uri("/api/orders/123")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("tenant_id 미설정 → 401")
    void protectedRoute_missingTenant_returns401() {
        String token = jwtHelper.signTokenWithIssuerAndTenant(
                "https://test.local/issuer", null);

        webTestClient.get()
                .uri("/api/orders/123")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("iss=global-account-platform (legacy) + tenant_id=ecommerce → 통과")
    void protectedRoute_legacyIssuer_passesJwtFilter() {
        String token = jwtHelper.signTokenWithIssuerAndTenant(
                "global-account-platform", "ecommerce");

        webTestClient.get()
                .uri("/api/orders/123")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status).isNotEqualTo(401));
    }

    @Test
    @DisplayName("iss=https://attacker.example.com → 401")
    void protectedRoute_attackerIssuer_returns401() {
        String token = jwtHelper.signTokenWithIssuerAndTenant(
                "https://attacker.example.com", "ecommerce");

        webTestClient.get()
                .uri("/api/orders/123")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }
}
