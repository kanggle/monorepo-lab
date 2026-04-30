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

import static com.github.tomakehurst.wiremock.client.WireMock.*;

@Tag("integration")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ExtendWith(DockerAvailableCondition.class)
@DisplayName("Gateway 통합 테스트")
class GatewayIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static WireMockServer authServiceMock;
    static WireMockServer accountServiceMock;
    static WireMockServer adminServiceMock;
    static KeyPair keyPair;

    @Autowired
    private WebTestClient webTestClient;

    @Autowired
    private ReactiveStringRedisTemplate redisTemplate;

    @BeforeAll
    static void beforeAll() throws Exception {

        // Generate RSA key pair
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(2048);
        keyPair = keyGen.generateKeyPair();

        // Start WireMock for auth-service
        authServiceMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        authServiceMock.start();

        // Start WireMock for account-service
        accountServiceMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        accountServiceMock.start();

        // Start WireMock for admin-service (second-layer auth — gateway must not
        // short-circuit admin paths with its own TOKEN_INVALID).
        adminServiceMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        adminServiceMock.start();

        // Setup JWKS endpoint
        RSAPublicKey rsaKey = (RSAPublicKey) keyPair.getPublic();
        String n = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(toUnsignedByteArray(rsaKey.getModulus()));
        String e = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(toUnsignedByteArray(rsaKey.getPublicExponent()));

        String jwksResponse = String.format("""
                {
                  "keys": [
                    {
                      "kty": "RSA",
                      "kid": "test-kid-1",
                      "use": "sig",
                      "alg": "RS256",
                      "n": "%s",
                      "e": "%s"
                    }
                  ]
                }
                """, n, e);

        authServiceMock.stubFor(get(urlEqualTo("/internal/auth/jwks"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(jwksResponse)));

        // Setup downstream auth-service login endpoint
        authServiceMock.stubFor(post(urlEqualTo("/api/auth/login"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accessToken\":\"mock-token\",\"refreshToken\":\"mock-refresh\"}")));

        // Setup downstream account-service me endpoint
        accountServiceMock.stubFor(get(urlEqualTo("/api/accounts/me"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"account-123\",\"email\":\"test@example.com\"}")));

        // Setup downstream account-service signup endpoint
        accountServiceMock.stubFor(post(urlEqualTo("/api/accounts/signup"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"new-account\"}")));

        // Setup internal provisioning endpoint
        accountServiceMock.stubFor(post(urlPathMatching("/internal/tenants/.+/accounts"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"id\":\"new-wms-account\"}")));

        // admin-service downstream — gateway must reach these without
        // performing its own JWT verification (operator tokens have a
        // separate JWKS owned by admin-service).
        adminServiceMock.stubFor(post(urlEqualTo("/api/admin/auth/login"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"ENROLLMENT_REQUIRED\",\"bootstrapToken\":\"mock-bootstrap\"}")));
        adminServiceMock.stubFor(get(urlEqualTo("/.well-known/admin/jwks.json"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"keys\":[]}")));
        adminServiceMock.stubFor(get(urlEqualTo("/api/admin/audit"))
                .willReturn(aResponse()
                        .withStatus(401)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"TOKEN_INVALID\",\"message\":\"Operator token missing\"}")));
    }

    @AfterAll
    static void afterAll() {
        if (authServiceMock != null) authServiceMock.stop();
        if (accountServiceMock != null) accountServiceMock.stop();
        if (adminServiceMock != null) adminServiceMock.stop();
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
        registry.add("spring.cloud.gateway.routes[2].uri", adminServiceMock::baseUrl);
        registry.add("spring.cloud.gateway.routes[2].id", () -> "admin-service");
        registry.add("spring.cloud.gateway.routes[2].predicates[0]",
                () -> "Path=/api/admin/**,/.well-known/admin/**");
        registry.add("spring.cloud.gateway.routes[3].uri", accountServiceMock::baseUrl);
        registry.add("spring.cloud.gateway.routes[3].id", () -> "account-service-internal");
        registry.add("spring.cloud.gateway.routes[3].predicates[0]", () -> "Path=/internal/tenants/**");
        // Fallback disabled by default
        registry.add("gateway.tenant.legacy-fallback.enabled", () -> "false");
    }

    @BeforeEach
    void setUp() {
        // Clean up rate limit keys
        redisTemplate.keys("rate:*")
                .flatMap(redisTemplate::delete)
                .collectList()
                .block();
    }

    // -----------------------------------------------------------------------
    // Existing tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("공개 경로 /api/auth/login 인증 없이 다운스트림 전달")
    void publicRoute_login_forwardsToDownstream() {
        webTestClient.post().uri("/api/auth/login")
                .header("Content-Type", "application/json")
                .bodyValue("{\"email\":\"test@example.com\",\"password\":\"password\"}")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.accessToken").isEqualTo("mock-token");
    }

    @Test
    @DisplayName("공개 경로 /api/accounts/signup 인증 없이 다운스트림 전달")
    void publicRoute_signup_forwardsToDownstream() {
        webTestClient.post().uri("/api/accounts/signup")
                .header("Content-Type", "application/json")
                .bodyValue("{\"email\":\"new@example.com\",\"password\":\"password1!\"}")
                .exchange()
                .expectStatus().isCreated();
    }

    @Test
    @DisplayName("인증 필요 경로 + 유효 JWT (tenant_id 포함) -> 200 + X-Account-ID + X-Tenant-Id 주입")
    void protectedRoute_validJwt_forwardsWithAccountIdAndTenantId() {
        String token = createValidToken("account-123", "fan-platform");

        webTestClient.get().uri("/api/accounts/me")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        // Verify downstream received X-Account-ID and X-Tenant-Id
        accountServiceMock.verify(getRequestedFor(urlEqualTo("/api/accounts/me"))
                .withHeader("X-Account-ID", equalTo("account-123"))
                .withHeader("X-Tenant-Id", equalTo("fan-platform")));
    }

    @Test
    @DisplayName("인증 필요 경로 + Authorization 헤더 없음 -> 401")
    void protectedRoute_noAuthHeader_returns401() {
        webTestClient.get().uri("/api/accounts/me")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TOKEN_INVALID");
    }

    @Test
    @DisplayName("만료된 JWT -> 401 TOKEN_INVALID")
    void protectedRoute_expiredJwt_returns401() {
        String expiredToken = Jwts.builder()
                .header().keyId("test-kid-1").and()
                .subject("account-123")
                .issuer(EXPECTED_ISSUER)
                .issuedAt(Date.from(Instant.now().minusSeconds(7200)))
                .expiration(Date.from(Instant.now().minusSeconds(3600)))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        webTestClient.get().uri("/api/accounts/me")
                .header("Authorization", "Bearer " + expiredToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TOKEN_INVALID");
    }

    @Test
    @DisplayName("변조된 JWT -> 401 TOKEN_INVALID")
    void protectedRoute_tamperedJwt_returns401() throws Exception {
        KeyPairGenerator otherKeyGen = KeyPairGenerator.getInstance("RSA");
        otherKeyGen.initialize(2048);
        KeyPair otherKeyPair = otherKeyGen.generateKeyPair();

        String tamperedToken = Jwts.builder()
                .header().keyId("test-kid-1").and()
                .subject("account-123")
                .issuer(EXPECTED_ISSUER)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(otherKeyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        webTestClient.get().uri("/api/accounts/me")
                .header("Authorization", "Bearer " + tamperedToken)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TOKEN_INVALID");
    }

    @Test
    @DisplayName("외부에서 X-Account-ID 직접 전송 -> gateway가 덮어씀")
    void spoofedAccountIdHeader_isOverwritten() {
        String token = createValidToken("real-account-id", "fan-platform");

        webTestClient.get().uri("/api/accounts/me")
                .header("Authorization", "Bearer " + token)
                .header("X-Account-ID", "spoofed-id")
                .exchange()
                .expectStatus().isOk();

        accountServiceMock.verify(getRequestedFor(urlEqualTo("/api/accounts/me"))
                .withHeader("X-Account-ID", equalTo("real-account-id")));
    }

    @Test
    @DisplayName("X-Request-ID가 없으면 자동 생성되어 전달됨")
    void requestId_generated_whenMissing() {
        webTestClient.post().uri("/api/auth/login")
                .header("Content-Type", "application/json")
                .bodyValue("{\"email\":\"test@example.com\",\"password\":\"password\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().exists("X-Request-ID");
    }

    @Test
    @DisplayName("X-Request-ID가 있으면 그대로 전파됨")
    void requestId_propagated_whenPresent() {
        String customRequestId = "custom-request-id-12345";

        webTestClient.post().uri("/api/auth/login")
                .header("Content-Type", "application/json")
                .header("X-Request-ID", customRequestId)
                .bodyValue("{\"email\":\"test@example.com\",\"password\":\"password\"}")
                .exchange()
                .expectStatus().isOk()
                .expectHeader().valueEquals("X-Request-ID", customRequestId);
    }

    @Test
    @DisplayName("/actuator/health 접근 가능")
    void actuatorHealth_returns200() {
        webTestClient.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk();
    }

    @Test
    @DisplayName("admin 로그인은 JWT 없이도 admin-service 까지 프록시됨 (gateway 무인증)")
    void adminLogin_passesThroughWithoutGatewayJwt() {
        webTestClient.post().uri("/api/admin/auth/login")
                .header("Content-Type", "application/json")
                .bodyValue("{\"operatorId\":\"admin\",\"password\":\"devpassword123!\"}")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                // downstream(admin-service) 의 응답이어야 함. gateway 가 단락시키면 TOKEN_INVALID.
                .jsonPath("$.code").isEqualTo("ENROLLMENT_REQUIRED")
                .jsonPath("$.bootstrapToken").isEqualTo("mock-bootstrap");

        adminServiceMock.verify(postRequestedFor(urlEqualTo("/api/admin/auth/login")));
    }

    @Test
    @DisplayName("admin JWKS 는 공개 — gateway 가 바로 admin-service 로 프록시")
    void adminJwks_isPublic() {
        webTestClient.get().uri("/.well-known/admin/jwks.json")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.keys").isArray();

        adminServiceMock.verify(getRequestedFor(urlEqualTo("/.well-known/admin/jwks.json")));
    }

    @Test
    @DisplayName("operator-protected admin 경로도 gateway 입장에선 public — downstream 이 operator 검증")
    void adminProtectedRoute_gatewaySkipsJwt_downstreamEnforces() {
        webTestClient.get().uri("/api/admin/audit")
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                // gateway 의 TOKEN_INVALID(account JWKS 기준)가 아니라,
                // admin-service OperatorAuthenticationFilter 가 보낸 응답이 그대로 전달돼야 한다.
                .jsonPath("$.code").isEqualTo("TOKEN_INVALID")
                .jsonPath("$.message").isEqualTo("Operator token missing");

        adminServiceMock.verify(getRequestedFor(urlEqualTo("/api/admin/audit")));
    }

    // -----------------------------------------------------------------------
    // TASK-BE-230: tenant propagation integration tests
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("JWT에 tenant_id 없음 + fallback 비활성 → 401 TOKEN_INVALID")
    void missingTenantIdClaim_fallbackDisabled_returns401() {
        // Token without tenant_id claim
        String tokenWithoutTenant = Jwts.builder()
                .header().keyId("test-kid-1").and()
                .subject("account-123")
                .issuer(EXPECTED_ISSUER)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();

        webTestClient.get().uri("/api/accounts/me")
                .header("Authorization", "Bearer " + tokenWithoutTenant)
                .exchange()
                .expectStatus().isUnauthorized()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TOKEN_INVALID");
    }

    @Test
    @DisplayName("클라이언트가 위조한 X-Tenant-Id 헤더 → gateway가 JWT 기반으로 덮어씀")
    void spoofedTenantIdHeader_isOverwrittenByJwtClaim() {
        String token = createValidToken("account-123", "fan-platform");

        webTestClient.get().uri("/api/accounts/me")
                .header("Authorization", "Bearer " + token)
                .header("X-Tenant-Id", "malicious-tenant")
                .exchange()
                .expectStatus().isOk();

        // Downstream must receive the JWT-derived tenant, not the spoofed one
        accountServiceMock.verify(getRequestedFor(urlEqualTo("/api/accounts/me"))
                .withHeader("X-Tenant-Id", equalTo("fan-platform")));
        accountServiceMock.verify(0, getRequestedFor(urlEqualTo("/api/accounts/me"))
                .withHeader("X-Tenant-Id", equalTo("malicious-tenant")));
    }

    @Test
    @DisplayName("tenant_id=fan-platform 토큰 → X-Tenant-Id: fan-platform 다운스트림 전파")
    void validToken_tenantIdFanPlatform_propagatesHeader() {
        String token = createValidToken("account-123", "fan-platform");

        webTestClient.get().uri("/api/accounts/me")
                .header("Authorization", "Bearer " + token)
                .exchange()
                .expectStatus().isOk();

        accountServiceMock.verify(getRequestedFor(urlEqualTo("/api/accounts/me"))
                .withHeader("X-Tenant-Id", equalTo("fan-platform")));
    }

    @Test
    @DisplayName("/internal/tenants/wms/accounts + JWT tenant_id=wms → 201 (path 일치)")
    void internalRoute_pathTenantMatchesJwt_passes() {
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
    @DisplayName("/internal/tenants/wms/accounts + JWT tenant_id=fan-platform → 403 TENANT_SCOPE_DENIED")
    void internalRoute_pathTenantMismatch_returns403() {
        String token = createValidToken("account-123", "fan-platform");

        webTestClient.post().uri("/internal/tenants/wms/accounts")
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .bodyValue("{\"email\":\"wms-user@wms.com\"}")
                .exchange()
                .expectStatus().isForbidden()
                .expectBody()
                .jsonPath("$.code").isEqualTo("TENANT_SCOPE_DENIED");
    }

    private static final String EXPECTED_ISSUER = "global-account-platform";

    private String createValidToken(String accountId, String tenantId) {
        return Jwts.builder()
                .header().keyId("test-kid-1").and()
                .subject(accountId)
                .issuer(EXPECTED_ISSUER)
                .claim("email", "test@example.com")
                .claim("tenant_id", tenantId)
                .issuedAt(Date.from(Instant.now()))
                .expiration(Date.from(Instant.now().plusSeconds(3600)))
                .signWith(keyPair.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }

    /**
     * Converts BigInteger to unsigned byte array (strips leading zero byte if present).
     */
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
