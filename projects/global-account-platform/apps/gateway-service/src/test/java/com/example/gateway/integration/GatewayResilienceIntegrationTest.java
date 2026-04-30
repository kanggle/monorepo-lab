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
@DisplayName("Gateway Resilience 통합 테스트 — JWKS rotation · 다운스트림 오류 · force-invalidated token")
class GatewayResilienceIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static WireMockServer authServiceMock;
    static WireMockServer accountServiceMock;

    /** Initial key pair (kid = "res-kid-1") */
    static KeyPair keyPairKid1;
    /** Rotated key pair (kid = "res-kid-2") registered after cache eviction */
    static KeyPair keyPairKid2;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @BeforeAll
    static void beforeAll() throws Exception {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        keyPairKid1 = keyGen.generateKeyPair();
        keyPairKid2 = keyGen.generateKeyPair();

        authServiceMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        authServiceMock.start();

        accountServiceMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        accountServiceMock.start();

        // Initial JWKS: kid-1 only
        authServiceMock.stubFor(get(urlEqualTo("/internal/auth/jwks"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(buildJwksResponse("res-kid-1", (RSAPublicKey) keyPairKid1.getPublic()))));

        accountServiceMock.stubFor(get(urlEqualTo("/api/accounts/me"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"account-r1\"}")));

        accountServiceMock.stubFor(get(urlEqualTo("/api/accounts/error500"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"internal server error\"}")));
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
        // Short grace period so in-memory fallback doesn't interfere
        registry.add("gateway.jwt.grace-period-seconds", () -> "5");
    }

    @BeforeEach
    void setUp() {
        // Clear JWKS Redis cache and rate limit keys
        redisTemplate.keys("jwks:*")
                .flatMap(redisTemplate::delete)
                .collectList()
                .block();
        redisTemplate.keys("ratelimit:*")
                .flatMap(redisTemplate::delete)
                .collectList()
                .block();
        redisTemplate.keys("access:invalidate-before:*")
                .flatMap(redisTemplate::delete)
                .collectList()
                .block();

        // Reset WireMock to kid-1 JWKS
        authServiceMock.resetMappings();
        authServiceMock.stubFor(get(urlEqualTo("/internal/auth/jwks"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(buildJwksResponse("res-kid-1", (RSAPublicKey) keyPairKid1.getPublic()))));
        authServiceMock.stubFor(post(urlEqualTo("/api/auth/login"))
                .willReturn(aResponse().withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accessToken\":\"t\"}")));
        accountServiceMock.resetMappings();
        accountServiceMock.stubFor(get(urlEqualTo("/api/accounts/me"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"account-r1\"}")));
        accountServiceMock.stubFor(get(urlEqualTo("/api/accounts/error500"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"error\":\"internal server error\"}")));
    }

    @Test
    @DisplayName("kid-1 유효 JWT → 정상 200 확인 (기준 검증)")
    void kid1Token_validJwt_returns200() {
        String token = createToken("res-kid-1", keyPairKid1, "account-r1");

        webTestClient.get().uri("/api/accounts/me")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("JWKS rotation — kid-2 토큰, Redis miss → refreshJwks() → 새 키로 검증 성공 (무중단)")
    void jwksRotation_kid2Token_triggersRefetchAndSucceeds() {
        // WireMock을 kid-2로 교체
        authServiceMock.resetMappings();
        authServiceMock.stubFor(get(urlEqualTo("/internal/auth/jwks"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(buildJwksResponse("res-kid-2", (RSAPublicKey) keyPairKid2.getPublic()))));

        // Redis 캐시 비우기 (kid-1 캐시 소멸 시뮬레이션)
        redisTemplate.keys("jwks:*")
                .flatMap(redisTemplate::delete)
                .collectList()
                .block();

        String token = createToken("res-kid-2", keyPairKid2, "account-r1");

        // kid-2 토큰 → JwksCache Redis miss → refreshJwks() → WireMock kid-2 반환 → 검증 성공
        webTestClient.get().uri("/api/accounts/me")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        // JWKS refetch 1회 이상 발생 확인
        authServiceMock.verify(moreThanOrExactly(1),
                getRequestedFor(urlEqualTo("/internal/auth/jwks")));
    }

    @Test
    @DisplayName("kid 불일치 + WireMock JWKS에도 없는 kid → 401 TOKEN_INVALID")
    void kidMismatch_notInJwks_returns401() {
        // WireMock은 kid-1만 반환, kid-unknown 없음
        String unknownKidToken = createToken("unknown-kid", keyPairKid2, "account-r1");

        webTestClient.get().uri("/api/accounts/me")
                .header("Authorization", "Bearer " + unknownKidToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TOKEN_INVALID");
    }

    @Test
    @DisplayName("다운스트림 HTTP 500 → 게이트웨이가 5xx 투명 전달")
    void downstream_http500_transparentlyProxied() {
        String token = createToken("res-kid-1", keyPairKid1, "account-r1");

        webTestClient.get().uri("/api/accounts/error500")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().is5xxServerError();
    }

    @Test
    @DisplayName("force-invalidated token — Redis invalidate-before > token iat → 401")
    void forceInvalidatedToken_olderThanInvalidationTimestamp_returns401() {
        String accountId = "account-force-inv";

        // iat = 1시간 전으로 토큰 생성
        Instant iat = Instant.now().minusSeconds(3600);
        String token = Jwts.builder()
                .header().keyId("res-kid-1").and()
                .subject(accountId)
                .issuer("global-account-platform")
                .issuedAt(Date.from(iat))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(keyPairKid1.getPrivate(), Jwts.SIG.RS256)
                .compact();

        // invalidate-before = 현재 시각 epoch millis (iat보다 나중)
        long invalidatedAtMillis = Instant.now().toEpochMilli();
        redisTemplate.opsForValue()
                .set("access:invalidate-before:" + accountId, String.valueOf(invalidatedAtMillis))
                .block();

        webTestClient.get().uri("/api/accounts/me")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TOKEN_INVALID");
    }

    @Test
    @DisplayName("force-invalidated Redis 키 없음 → fail-open, 정상 토큰 통과")
    void forceInvalidatedKey_absent_failsOpen() {
        String token = createToken("res-kid-1", keyPairKid1, "account-no-inv");

        webTestClient.get().uri("/api/accounts/me")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();
    }

    private String createToken(String kid, KeyPair kp, String accountId) {
        return Jwts.builder()
                .header().keyId(kid).and()
                .subject(accountId)
                .issuer("global-account-platform")
                .claim("email", "test@example.com")
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(kp.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    private static String buildJwksResponse(String kid, RSAPublicKey rsaKey) {
        String n = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(toUnsignedByteArray(rsaKey.getModulus()));
        String e = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(toUnsignedByteArray(rsaKey.getPublicExponent()));
        return String.format(
                "{\"keys\":[{\"kty\":\"RSA\",\"kid\":\"%s\",\"use\":\"sig\",\"alg\":\"RS256\",\"n\":\"%s\",\"e\":\"%s\"}]}",
                kid, n, e);
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
