package com.example.gateway.integration;

import org.junit.jupiter.api.Tag;
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
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for TASK-BE-230: tenant_id propagation, rate limit key patterns,
 * fallback toggle, and internal route tenant scope validation.
 */
@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("Gateway Tenant Propagation 통합 테스트")
class GatewayTenantPropagationIntegrationTest {

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
                {"keys":[{"kty":"RSA","kid":"tenant-test-kid","use":"sig","alg":"RS256","n":"%s","e":"%s"}]}
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

        accountServiceMock.stubFor(post(urlPathMatching("/internal/tenants/.+/accounts"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"provisioned-account\"}")));
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
        registry.add("spring.cloud.gateway.routes[2].uri", accountServiceMock::baseUrl);
        registry.add("spring.cloud.gateway.routes[2].id", () -> "account-service-internal");
        registry.add("spring.cloud.gateway.routes[2].predicates[0]", () -> "Path=/internal/tenants/**");
        // Tight login rate limit for key pattern verification
        registry.add("gateway.rate-limit.login.max-requests", () -> "100");
        registry.add("gateway.rate-limit.login.window-seconds", () -> "60");
        // Fallback enabled for fallback-related tests
        registry.add("gateway.tenant.legacy-fallback.enabled", () -> "true");
        registry.add("gateway.tenant.legacy-fallback.default-tenant-id", () -> "fan-platform");
    }

    @BeforeEach
    void setUp() {
        redisTemplate.keys("rate:*")
                .flatMap(redisTemplate::delete)
                .collectList()
                .block();
    }

    @Test
    @DisplayName("login scope rate limit 키가 tenant_id를 포함 — fan-platform:{subnet} 패턴")
    void loginScope_rateLimitKey_includesTenantId() {
        String clientIp = "10.10.10.1";
        // login is public, no JWT needed — tenant_id extracted as "anonymous"
        webTestClient.post().uri("/api/auth/login")
                .header("Content-Type", "application/json")
                .header("X-Forwarded-For", clientIp)
                .bodyValue("{\"email\":\"a@a.com\",\"password\":\"pw\"}")
                .exchange()
                .expectStatus().isOk();

        // Redis should contain a key with the anonymous tenant prefix
        List<String> keys = redisTemplate.keys("rate:login:*")
                .collectList()
                .block();

        assertThat(keys).isNotEmpty();
        // Key must follow "rate:login:{tenant_id}:{subnet}" pattern
        assertThat(keys).anyMatch(k -> k.contains("anonymous:"));
    }

    @Test
    @DisplayName("JWT tenant_id=fan-platform인 요청의 rate limit 키 = fan-platform:... 패턴")
    void loginScope_withJwt_rateLimitKeyIncludesTenant() {
        // For login route (public), JWT is optional, but we can still send one
        // to verify tenant extraction in rate limit key
        String clientIp = "10.20.20.1";
        String token = createValidToken("account-123", "fan-platform");

        webTestClient.post().uri("/api/auth/login")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + token)
                .header("X-Forwarded-For", clientIp)
                .bodyValue("{\"email\":\"a@a.com\",\"password\":\"pw\"}")
                .exchange()
                .expectStatus().isOk();

        List<String> keys = redisTemplate.keys("rate:login:*")
                .collectList()
                .block();

        assertThat(keys).isNotEmpty();
        assertThat(keys).anyMatch(k -> k.contains("fan-platform:"));
    }

    @Test
    @DisplayName("grace period fallback 활성 + tenant_id 없는 토큰 → fan-platform으로 통과 + X-Tenant-Id: fan-platform")
    void fallbackEnabled_missingTenantIdClaim_passesAsFanPlatform() {
        // Token without tenant_id claim (grace period fallback enabled via DynamicPropertySource)
        String tokenWithoutTenant = Jwts.builder()
                .header().keyId("tenant-test-kid").and()
                .subject("account-123")
                .issuer(EXPECTED_ISSUER)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        webTestClient.get().uri("/api/accounts/me")
                .header("Authorization", "Bearer " + tokenWithoutTenant)
                .exchange()
                .expectStatus().isOk();

        // Downstream must receive X-Tenant-Id: fan-platform (fallback value)
        accountServiceMock.verify(getRequestedFor(urlEqualTo("/api/accounts/me"))
                .withHeader("X-Tenant-Id", equalTo("fan-platform")));
    }

    @Test
    @DisplayName("내부 provisioning 경로 + 일치하는 tenant_id → 다운스트림 전달 + X-Tenant-Id: wms")
    void internalProvisioning_matchingTenant_forwardsToDownstream() {
        String token = createValidToken("service-account", "wms");

        webTestClient.post().uri("/internal/tenants/wms/accounts")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .bodyValue("{\"email\":\"wms-user@wms.com\"}")
                .exchange()
                .expectStatus().isCreated();

        accountServiceMock.verify(postRequestedFor(urlEqualTo("/internal/tenants/wms/accounts"))
                .withHeader("X-Tenant-Id", equalTo("wms")));
    }

    @Test
    @DisplayName("내부 provisioning 경로 + JWT tenant_id 불일치 → 403 TENANT_SCOPE_DENIED (다운스트림 미전달)")
    void internalProvisioning_mismatchedTenant_returns403_withoutForwardingToDownstream() {
        String token = createValidToken("account-123", "fan-platform");

        webTestClient.post().uri("/internal/tenants/wms/accounts")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .bodyValue("{\"email\":\"wms-user@wms.com\"}")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TENANT_SCOPE_DENIED");

        // Downstream must NOT have been called
        accountServiceMock.verify(0, postRequestedFor(urlEqualTo("/internal/tenants/wms/accounts")));
    }

    @Test
    @DisplayName("공개 경로 (/actuator/health) — tenant 검증 없이 정상 동작 (회귀 없음)")
    void publicHealthRoute_noTenantValidation_works() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    private static final String EXPECTED_ISSUER = "global-account-platform";

    private String createValidToken(String accountId, String tenantId) {
        return Jwts.builder()
                .header().keyId("tenant-test-kid").and()
                .subject(accountId)
                .issuer(EXPECTED_ISSUER)
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
