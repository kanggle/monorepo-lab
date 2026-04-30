package com.example.gateway.integration;

import com.example.testsupport.integration.DockerAvailableCondition;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import io.jsonwebtoken.Jwts;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("Gateway Rate Limit 통합 테스트")
class GatewayRateLimitIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static WireMockServer authServiceMock;
    static WireMockServer accountServiceMock;
    static KeyPair keyPair;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @BeforeAll
    static void beforeAll() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        keyPair = keyGen.generateKeyPair();

        authServiceMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        authServiceMock.start();

        accountServiceMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        accountServiceMock.start();

        RSAPublicKey rsaKey = (RSAPublicKey) keyPair.getPublic();
        String n = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(toUnsignedByteArray(rsaKey.getModulus()));
        String e = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(toUnsignedByteArray(rsaKey.getPublicExponent()));

        String jwksResponse = String.format("""
                {"keys":[{"kty":"RSA","kid":"rl-test-kid","use":"sig","alg":"RS256","n":"%s","e":"%s"}]}
                """, n, e);

        authServiceMock.stubFor(get(urlEqualTo("/internal/auth/jwks"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(jwksResponse)));

        authServiceMock.stubFor(post(urlEqualTo("/api/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accessToken\":\"mock-token\"}")));

        accountServiceMock.stubFor(get(urlEqualTo("/api/accounts/me"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"account-123\"}")));
    }

    @AfterAll
    static void afterAll() {
        if (authServiceMock != null) authServiceMock.stop();
        if (accountServiceMock != null) accountServiceMock.stop();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("gateway.jwt.jwks-url",
                () -> authServiceMock.baseUrl() + "/internal/auth/jwks");
        registry.add("spring.cloud.gateway.routes[0].uri", authServiceMock::baseUrl);
        registry.add("spring.cloud.gateway.routes[0].id", () -> "auth-service");
        registry.add("spring.cloud.gateway.routes[0].predicates[0]", () -> "Path=/api/auth/**");
        registry.add("spring.cloud.gateway.routes[1].uri", accountServiceMock::baseUrl);
        registry.add("spring.cloud.gateway.routes[1].id", () -> "account-service");
        registry.add("spring.cloud.gateway.routes[1].predicates[0]", () -> "Path=/api/accounts/**");
        // login scope max=2 for fast rate-limit testing
        registry.add("gateway.rate-limit.login.max-requests", () -> "2");
        registry.add("gateway.rate-limit.login.window-seconds", () -> "60");
    }

    @BeforeEach
    void setUp() {
        redisTemplate.keys("rate:*")
                .flatMap(redisTemplate::delete)
                .collectList()
                .block();
    }

    @Test
    @DisplayName("login scope max 초과 3번째 요청 → 429 + Retry-After 헤더")
    void loginScope_thirdRequest_returns429WithRetryAfterHeader() {
        String clientIp = "10.0.0.1";

        // 첫 번째 — OK
        webTestClient.post().uri("/api/auth/login")
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", clientIp)
                .bodyValue("{\"email\":\"a@a.com\",\"password\":\"pw\"}")
                .exchange()
                .expectStatus().isOk();

        // 두 번째 — OK
        webTestClient.post().uri("/api/auth/login")
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", clientIp)
                .bodyValue("{\"email\":\"a@a.com\",\"password\":\"pw\"}")
                .exchange()
                .expectStatus().isOk();

        // 세 번째 — 429
        webTestClient.post().uri("/api/auth/login")
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", clientIp)
                .bodyValue("{\"email\":\"a@a.com\",\"password\":\"pw\"}")
                .exchange()
                .expectStatus().isEqualTo(429)
                .expectHeader().exists("Retry-After")
                .expectBody()
                .jsonPath("$.code").isEqualTo("RATE_LIMITED");
    }

    @Test
    @DisplayName("서로 다른 IP는 독립된 rate limit 카운터를 가진다")
    void differentIps_haveIndependentRateLimitCounters() {
        String ip1 = "10.1.1.1";
        String ip2 = "10.2.2.2";

        // ip1 두 번 → limit 도달
        for (int i = 0; i < 2; i++) {
            webTestClient.post().uri("/api/auth/login")
                    .header("Content-Type", "application/json")
                    .header("X-Forwarded-For", ip1)
                    .bodyValue("{\"email\":\"a@a.com\",\"password\":\"pw\"}")
                    .exchange()
                    .expectStatus().isOk();
        }
        // ip1 세 번째 → 429
        webTestClient.post().uri("/api/auth/login")
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", ip1)
                .bodyValue("{\"email\":\"a@a.com\",\"password\":\"pw\"}")
                .exchange()
                .expectStatus().isEqualTo(429);

        // ip2 첫 번째 → 여전히 OK (별도 카운터)
        webTestClient.post().uri("/api/auth/login")
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", ip2)
                .bodyValue("{\"email\":\"a@a.com\",\"password\":\"pw\"}")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("rate limit 초과 후 Redis 키 삭제(window 리셋 시뮬레이션) → 다시 허용")
    void rateLimitExceeded_afterWindowReset_allowsAgain() {
        String clientIp = "10.3.3.3";

        // 두 번 소진 후 세 번째 429
        for (int i = 0; i < 2; i++) {
            webTestClient.post().uri("/api/auth/login")
                    .header("Content-Type", "application/json")
                    .header("X-Forwarded-For", clientIp)
                    .bodyValue("{\"email\":\"a@a.com\",\"password\":\"pw\"}")
                    .exchange()
                    .expectStatus().isOk();
        }
        webTestClient.post().uri("/api/auth/login")
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", clientIp)
                .bodyValue("{\"email\":\"a@a.com\",\"password\":\"pw\"}")
                .exchange()
                .expectStatus().isEqualTo(429);

        // Redis 키 삭제 (window 만료 시뮬레이션)
        redisTemplate.keys("rate:login:*")
                .flatMap(redisTemplate::delete)
                .collectList()
                .block();

        // 다시 허용
        webTestClient.post().uri("/api/auth/login")
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", clientIp)
                .bodyValue("{\"email\":\"a@a.com\",\"password\":\"pw\"}")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("다운스트림 연결 리셋(Fault) → 게이트웨이가 5xx 반환")
    void downstream_connectionFault_returns5xx() {
        accountServiceMock.stubFor(get(urlEqualTo("/api/accounts/downstream-fail"))
                .willReturn(aResponse()
                        .withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        String token = createValidToken("account-123", "fan-platform");
        webTestClient.get().uri("/api/accounts/downstream-fail")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().is5xxServerError();
    }

    private String createValidToken(String accountId, String tenantId) {
        return Jwts.builder()
                .header().keyId("rl-test-kid").and()
                .subject(accountId)
                .issuer("global-account-platform")
                .claim("email", "test@example.com")
                .claim("tenant_id", tenantId)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static byte[] toUnsignedByteArray(BigInteger value) {
        byte[] bytes = value.toByteArray();
        if (bytes[0] == 0) {
            byte[] result = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, result, 0, result.length);
            return result;
        }
        return bytes;
    }
}
