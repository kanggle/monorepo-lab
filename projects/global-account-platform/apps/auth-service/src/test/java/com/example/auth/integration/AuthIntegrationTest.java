package com.example.auth.integration;

import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
import com.example.auth.infrastructure.persistence.CredentialJpaEntity;
import com.example.auth.infrastructure.persistence.CredentialJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gap.security.password.Argon2idPasswordHasher;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class AuthIntegrationTest extends AbstractIntegrationTest {

    // MySQL + Kafka inherited from AbstractIntegrationTest (TASK-BE-076/078).
    // Redis remains service-specific.
    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static WireMockServer wireMock;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CredentialJpaRepository credentialJpaRepository;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String TEST_EMAIL = "user@example.com";
    private static final String TEST_PASSWORD = "password123";
    private static final String ACCOUNT_ID = "acc-integration-test";
    private static final String LOCKED_EMAIL = "locked@example.com";
    private static final String LOCKED_ACCOUNT_ID = "acc-locked";

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MySQL + Kafka registered by AbstractIntegrationTest.
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("auth.account-service.base-url", wireMock::baseUrl);
    }

    @BeforeEach
    void setup() {
        wireMock.resetAll();

        // TASK-BE-063: credentials now live in auth_db; seed the active account
        // row locally and stub account-service for the status-only check.
        credentialJpaRepository.deleteAll();
        Argon2idPasswordHasher hasher = new Argon2idPasswordHasher();
        String hash = hasher.hash(TEST_PASSWORD);
        Instant now = Instant.now();
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(
                Credential.create(ACCOUNT_ID, TEST_EMAIL, CredentialHash.argon2id(hash), now)));

        // Status-only stub used by LoginUseCase after local credential lookup
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/accounts/" + ACCOUNT_ID + "/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "accountId": "%s",
                                    "status": "ACTIVE",
                                    "statusChangedAt": "%s"
                                }
                                """.formatted(ACCOUNT_ID, now.toString()))));
    }

    @Test
    @Order(1)
    @DisplayName("Login succeeds and returns token pair")
    void loginSuccess() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(TEST_EMAIL, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("accessToken");
    }

    @Test
    @Order(2)
    @DisplayName("Login fails with wrong password")
    void loginFailsWrongPassword() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"wrongpassword1"}
                                """.formatted(TEST_EMAIL)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CREDENTIALS_INVALID"));
    }

    @Test
    @Order(3)
    @DisplayName("Login fails with unknown email (no local credential)")
    void loginFailsUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"unknown@example.com","password":"password123"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("CREDENTIALS_INVALID"));
    }

    @Test
    @Order(4)
    @DisplayName("Login rate limit after 5 failures")
    void loginRateLimit() throws Exception {
        // TASK-MONO-023b fix: TASK-BE-229 changed key pattern to login:fail:{tenantId}:{emailHash}.
        // LoginUseCase uses TenantContext.DEFAULT_TENANT_ID ("fan-platform") when no tenantId
        // is present in the request. Tests must seed the 3-part key so the rate-limit check fires.
        String emailHash = hashEmail(TEST_EMAIL);
        String key = "login:fail:fan-platform:" + emailHash;
        redisTemplate.delete(key);

        redisTemplate.opsForValue().set(key, "5");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"password123"}
                                """.formatted(TEST_EMAIL)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("LOGIN_RATE_LIMITED"));

        redisTemplate.delete(key);
    }

    @Test
    @Order(5)
    @DisplayName("Login and then refresh token")
    void loginAndRefresh() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(TEST_EMAIL, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(responseBody).get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(1800));
    }

    @Test
    @Order(6)
    @DisplayName("Login and then logout")
    void loginAndLogout() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(TEST_EMAIL, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody = loginResult.getResponse().getContentAsString();
        String refreshToken = objectMapper.readTree(responseBody).get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refreshToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @Order(65)
    @DisplayName("Refresh token reuse → 401 TOKEN_REUSE_DETECTED, all sessions revoked, Redis marker set")
    void refreshTokenReuseDetected() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(TEST_EMAIL, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
        String originalRefresh = objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .get("refreshToken").asText();

        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(originalRefresh)))
                .andExpect(status().isOk());

        // Replay the original refresh token. TASK-BE-062 §B: reuse detection must run before
        // the blacklist / revoked checks so the incident-response path (Redis marker set,
        // every device_session revoked) runs. If we get here with SESSION_REVOKED it means
        // the ordering regressed.
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(originalRefresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REUSE_DETECTED"));

        Boolean hasMarker = redisTemplate.hasKey("refresh:invalidate-all:" + ACCOUNT_ID);
        assertThat(hasMarker).isTrue();
    }

    @Test
    @Order(7)
    @DisplayName("JWKS endpoint returns valid JWKS")
    void jwksEndpoint() throws Exception {
        mockMvc.perform(get("/internal/auth/jwks"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.keys").isArray())
                .andExpect(jsonPath("$.keys[0].kty").value("RSA"))
                .andExpect(jsonPath("$.keys[0].alg").value("RS256"))
                .andExpect(jsonPath("$.keys[0].use").value("sig"));
    }

    @Test
    @Order(8)
    @DisplayName("Account-status service down → login returns 503 (fail-closed)")
    void accountServiceDown() throws Exception {
        wireMock.resetAll();
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/accounts/" + ACCOUNT_ID + "/status"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withFixedDelay(6000)));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(TEST_EMAIL, TEST_PASSWORD)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("SERVICE_UNAVAILABLE"));
    }

    @Test
    @Order(9)
    @DisplayName("Locked account → 403 ACCOUNT_LOCKED")
    void loginLockedAccount() throws Exception {
        // Seed a locked-user credential row
        Argon2idPasswordHasher hasher = new Argon2idPasswordHasher();
        String hash = hasher.hash(TEST_PASSWORD);
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(
                Credential.create(LOCKED_ACCOUNT_ID, LOCKED_EMAIL,
                        CredentialHash.argon2id(hash), Instant.now())));

        wireMock.resetAll();
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/accounts/" + LOCKED_ACCOUNT_ID + "/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "accountId": "%s",
                                    "status": "LOCKED",
                                    "statusChangedAt": "%s"
                                }
                                """.formatted(LOCKED_ACCOUNT_ID, Instant.now().toString()))));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(LOCKED_EMAIL, TEST_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
    }

    @Test
    @Order(10)
    @DisplayName("TASK-BE-063: POST /internal/auth/credentials seeds a credential that subsequent login can use")
    void internalCredentialCreateEndToEnd() throws Exception {
        String newAccountId = "acc-e2e-" + System.currentTimeMillis();
        String newEmail = "e2e-" + System.currentTimeMillis() + "@example.com";

        // Stub status lookup for the new account
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/accounts/" + newAccountId + "/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {
                                    "accountId": "%s",
                                    "status": "ACTIVE",
                                    "statusChangedAt": "%s"
                                }
                                """.formatted(newAccountId, Instant.now().toString()))));

        // Call the internal credential-create endpoint (account-service would do this
        // during signup). Then log in with the same email+password.
        mockMvc.perform(post("/internal/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "%s",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(newAccountId, newEmail, TEST_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accountId").value(newAccountId));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(newEmail, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @Order(11)
    @DisplayName("TASK-BE-063: duplicate credential create returns 409")
    void duplicateCredentialReturns409() throws Exception {
        mockMvc.perform(post("/internal/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "%s",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(ACCOUNT_ID, TEST_EMAIL, TEST_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREDENTIAL_ALREADY_EXISTS"));
    }

    private static String hashEmail(String email) {
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(email.toLowerCase().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(hash).substring(0, 10);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
