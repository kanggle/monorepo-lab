package com.example.gateway;

import com.redis.testcontainers.RedisContainer;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Tag("integration")
@Testcontainers
@ActiveProfiles("integration-test")
@DisplayName("Gateway 통합 테스트")
class GatewayIntegrationTest {

    private static final String JWT_SECRET = "integration-test-jwt-secret-key-minimum-32-chars!!";
    private static final SecretKey KEY = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));

    @Container
    static RedisContainer redis = new RedisContainer(DockerImageName.parse("redis:7-alpine"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("jwt.secret", () -> JWT_SECRET);
    }

    @Autowired
    private WebTestClient webTestClient;

    private String validToken() {
        return Jwts.builder()
                .subject("user-123")
                .claim("email", "user@example.com")
                .expiration(new Date(System.currentTimeMillis() + 3600_000))
                .signWith(KEY)
                .compact();
    }

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
    @DisplayName("유효한 JWT로 보호된 경로 요청 시 JWT 필터를 통과한다 (다운스트림 불가 → 5xx)")
    void protectedRoute_validToken_passesJwtFilter() {
        webTestClient.get()
                .uri("/api/orders/123")
                .header("Authorization", "Bearer " + validToken())
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isNotEqualTo(401));
    }

    @Test
    @DisplayName("공개 경로 POST /api/auth/login은 토큰 없이 JWT 필터를 통과한다 (다운스트림 불가 → 5xx)")
    void publicRoute_login_passesWithoutToken() {
        webTestClient.post()
                .uri("/api/auth/login")
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isNotEqualTo(401));
    }

    @Test
    @DisplayName("공개 경로 GET /api/products/**는 토큰 없이 JWT 필터를 통과한다 (다운스트림 불가 → 5xx)")
    void publicRoute_products_passesWithoutToken() {
        webTestClient.get()
                .uri("/api/products/42")
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isNotEqualTo(401));
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

    @Test
    @DisplayName("만료된 JWT로 보호된 경로 요청 시 401을 반환한다")
    void protectedRoute_expiredToken_returns401() {
        String expiredToken = Jwts.builder()
                .subject("user-123")
                .claim("email", "user@example.com")
                .expiration(new Date(System.currentTimeMillis() - 1000))
                .signWith(KEY)
                .compact();

        webTestClient.get()
                .uri("/api/orders/123")
                .header("Authorization", "Bearer " + expiredToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("공개 경로 POST /api/auth/refresh는 토큰 없이 JWT 필터를 통과한다")
    void publicRoute_refresh_passesWithoutToken() {
        webTestClient.post()
                .uri("/api/auth/refresh")
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isNotEqualTo(401));
    }

    @Test
    @DisplayName("공개 경로 GET /api/search/**는 토큰 없이 JWT 필터를 통과한다")
    void publicRoute_search_passesWithoutToken() {
        webTestClient.get()
                .uri("/api/search/products?q=shoes")
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isNotEqualTo(401));
    }

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

    @Test
    @DisplayName("공개 경로 POST /api/auth/signup은 토큰 없이 JWT 필터를 통과한다")
    void publicRoute_signup_passesWithoutToken() {
        webTestClient.post()
                .uri("/api/auth/signup")
                .exchange()
                .expectStatus().value(status ->
                        org.assertj.core.api.Assertions.assertThat(status)
                                .isNotEqualTo(401));
    }
}
