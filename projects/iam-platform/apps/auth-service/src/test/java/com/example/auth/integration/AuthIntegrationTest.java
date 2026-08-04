package com.example.auth.integration;

import com.example.auth.application.LoginUseCase;
import com.example.auth.application.command.LoginCommand;
import com.example.auth.application.exception.AccountLockedException;
import com.example.auth.application.exception.AccountServiceUnavailableException;
import com.example.auth.application.exception.CredentialsInvalidException;
import com.example.auth.application.exception.LoginRateLimitedException;
import com.example.auth.application.result.LoginResult;
import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
import com.example.auth.domain.session.SessionContext;
import com.example.auth.infrastructure.persistence.CredentialJpaEntity;
import com.example.auth.infrastructure.persistence.CredentialJpaRepository;
import com.example.security.password.Argon2idPasswordHasher;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// TASK-MONO-044c-1 RC#2: see SocialLoginSasBrowserIntegrationTest for rationale —
// classes that own their WireMock and override auth.account-service.base-url
// via @DynamicPropertySource must isolate Spring contexts to avoid the
// AccountServiceClient bean capturing another class's now-stopped WireMock URL.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
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

    // TASK-BE-318c: AccountServiceClient now mints a GAP client_credentials Bearer token via a SAS
    // self-call to /oauth2/token, unreachable in @SpringBootTest+MockMvc. Replace the provider with
    // a mock returning a fixed bearer so account stubs are exercised hermetically.
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.example.security.oauth2.client.IamClientCredentialsTokenProvider gapTokenProvider;

    @org.junit.jupiter.api.BeforeEach
    void stubIamClientCredentialsToken() {
        org.mockito.Mockito.when(gapTokenProvider.currentBearer()).thenReturn("test-jwt");
    }

    // TASK-BE-398: POST /api/auth/login was removed at its ADR-001 D2-b sunset, so the
    // password-login scenarios below (and the token minting the refresh/logout scenarios
    // need) drive LoginUseCase directly. The integration value is unchanged — real MySQL
    // credentials, real Redis failure counter, real account-service HTTP via WireMock —
    // only the (now absent) HTTP entry point is gone.
    @Autowired
    private LoginUseCase loginUseCase;

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
    void loginSuccess() {
        LoginResult result = login(TEST_EMAIL, TEST_PASSWORD);

        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.refreshToken()).isNotBlank();
        assertThat(result.expiresIn()).isEqualTo(1800);
    }

    @Test
    @Order(2)
    @DisplayName("Login fails with wrong password")
    void loginFailsWrongPassword() {
        assertThatThrownBy(() -> login(TEST_EMAIL, "wrongpassword1"))
                .isInstanceOf(CredentialsInvalidException.class);
    }

    @Test
    @Order(3)
    @DisplayName("Login fails with unknown email (no local credential)")
    void loginFailsUnknownEmail() {
        assertThatThrownBy(() -> login("unknown@example.com", "password123"))
                .isInstanceOf(CredentialsInvalidException.class);
    }

    @Test
    @Order(4)
    @DisplayName("Login rate limit after 5 failures")
    void loginRateLimit() {
        // TASK-MONO-023b fix: TASK-BE-229 changed key pattern to login:fail:{tenantId}:{emailHash}.
        // LoginUseCase uses TenantContext.DEFAULT_TENANT_ID ("fan-platform") when no tenantId
        // is present in the request. Tests must seed the 3-part key so the rate-limit check fires.
        String emailHash = hashEmail(TEST_EMAIL);
        String key = "login:fail:fan-platform:" + emailHash;
        redisTemplate.delete(key);

        redisTemplate.opsForValue().set(key, "5");

        assertThatThrownBy(() -> login(TEST_EMAIL, "password123"))
                .isInstanceOf(LoginRateLimitedException.class);

        redisTemplate.delete(key);
    }

    @Test
    @Order(5)
    @DisplayName("Login and then refresh token")
    void loginAndRefresh() throws Exception {
        String refreshToken = login(TEST_EMAIL, TEST_PASSWORD).refreshToken();

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
        String refreshToken = login(TEST_EMAIL, TEST_PASSWORD).refreshToken();

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
        String originalRefresh = login(TEST_EMAIL, TEST_PASSWORD).refreshToken();

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
    @DisplayName("Account-status service down → login fails closed (AccountServiceUnavailable)")
    void accountServiceDown() {
        wireMock.resetAll();
        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/accounts/" + ACCOUNT_ID + "/status"))
                .willReturn(aResponse()
                        .withStatus(503)
                        .withFixedDelay(6000)));

        assertThatThrownBy(() -> login(TEST_EMAIL, TEST_PASSWORD))
                .isInstanceOf(AccountServiceUnavailableException.class);
    }

    @Test
    @Order(9)
    @DisplayName("Locked account → ACCOUNT_LOCKED")
    void loginLockedAccount() {
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

        assertThatThrownBy(() -> login(LOCKED_EMAIL, TEST_PASSWORD))
                .isInstanceOf(AccountLockedException.class);
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

        assertThat(login(newEmail, TEST_PASSWORD).accessToken()).isNotBlank();
    }

    @Test
    @Order(11)
    @DisplayName("TASK-BE-063: duplicate credential create returns 409")
    void duplicateCredentialReturns409() throws Exception {
        // TASK-MONO-044c: TASK-BE-247 made the use case idempotent for the same (accountId, email)
        // pair (returns 200 with idempotent flag). To exercise the genuine-conflict path that
        // still returns 409, post the same accountId with a *different* email.
        mockMvc.perform(post("/internal/auth/credentials")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "accountId": "%s",
                                  "email": "different-%s",
                                  "password": "%s"
                                }
                                """.formatted(ACCOUNT_ID, TEST_EMAIL, TEST_PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CREDENTIAL_ALREADY_EXISTS"));
    }

    /**
     * Drives the password-login use case directly. TASK-BE-398 removed
     * {@code POST /api/auth/login} (ADR-001 D2-b sunset), so this replaces the former
     * MockMvc call — same use case, same transaction, same side effects.
     */
    private LoginResult login(String email, String password) {
        return loginUseCase.execute(new LoginCommand(
                email, password, null,
                new SessionContext("127.0.0.1", "IntegrationTest/1.0", "fp-integration")));
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
