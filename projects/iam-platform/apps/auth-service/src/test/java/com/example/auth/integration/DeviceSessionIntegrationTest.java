package com.example.auth.integration;

import com.example.auth.domain.credentials.Credential;
import com.example.auth.domain.credentials.CredentialHash;
import com.example.auth.domain.session.DeviceSession;
import com.example.auth.domain.session.RevokeReason;
import com.example.auth.domain.repository.DeviceSessionRepository;
import com.example.auth.domain.repository.RefreshTokenRepository;
import com.example.auth.infrastructure.persistence.CredentialJpaEntity;
import com.example.auth.infrastructure.persistence.CredentialJpaRepository;
import com.example.auth.infrastructure.persistence.AuthOutboxJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.security.password.Argon2idPasswordHasher;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
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
import java.util.List;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration for device/session management in auth-service.
 *
 * <p>Covers the Phase A closing scenarios from TASK-BE-026:
 * <ol>
 *   <li>Two logins from distinct user-agents surface as two sessions.
 *   <li>Exceeding {@code max-active-sessions} evicts the oldest and records the outbox event.
 *   <li>Revoking one session invalidates its refresh token.
 *   <li>Bulk revoke keeps only the current session.
 * </ol>
 *
 */
// TASK-MONO-044c-1 RC#2: see OAuthLoginIntegrationTest for rationale —
// AccountServiceClient bean URL must be rebuilt per class to track this
// class's WireMock instance.
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DeviceSessionIntegrationTest extends AbstractIntegrationTest {

    // MySQL + Kafka inherited from AbstractIntegrationTest (TASK-BE-076/078).
    // Redis remains service-specific.
    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static WireMockServer wireMock;

    @Autowired private MockMvc mockMvc;

    // TASK-BE-318c: AccountServiceClient now mints a GAP client_credentials Bearer token via a SAS
    // self-call to /oauth2/token, unreachable in @SpringBootTest+MockMvc. Replace the provider with
    // a mock returning a fixed bearer so account stubs are exercised hermetically.
    @org.springframework.test.context.bean.override.mockito.MockitoBean
    com.example.auth.infrastructure.client.IamClientCredentialsTokenProvider gapTokenProvider;

    @org.junit.jupiter.api.BeforeEach
    void stubIamClientCredentialsToken() {
        org.mockito.Mockito.when(gapTokenProvider.currentBearer()).thenReturn("test-jwt");
    }
    @Autowired private ObjectMapper objectMapper;
    @Autowired private DeviceSessionRepository deviceSessionRepository;
    @Autowired private AuthOutboxJpaRepository outboxJpaRepository;
    @Autowired private CredentialJpaRepository credentialJpaRepository;
    // TASK-MONO-393: the DB is the only place the scoping property can be asserted without
    // Redis being able to answer for it; redisTemplate is used only to name an outage.
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private StringRedisTemplate redisTemplate;

    private static final String TEST_EMAIL = "session-it@example.com";
    private static final String TEST_PASSWORD = "password-session-1";
    private static final String ACCOUNT_ID = "acc-device-session-it";

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) wireMock.stop();
    }

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MySQL + Kafka registered by AbstractIntegrationTest.
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("auth.account-service.base-url", wireMock::baseUrl);
        registry.add("auth.device-session.max-active-sessions", () -> "10");
    }

    @BeforeEach
    void stubAccountService() {
        wireMock.resetAll();

        // TASK-BE-063: credential lookup is now local. Seed the credentials row
        // before each test and stub account-service for the status-only check.
        credentialJpaRepository.deleteAll();
        Instant now = Instant.now();
        String hash = new Argon2idPasswordHasher().hash(TEST_PASSWORD);
        credentialJpaRepository.save(CredentialJpaEntity.fromDomain(
                Credential.create(ACCOUNT_ID, TEST_EMAIL, CredentialHash.argon2id(hash), now)));

        wireMock.stubFor(WireMock.get(urlPathEqualTo("/internal/accounts/" + ACCOUNT_ID + "/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"accountId":"%s","status":"ACTIVE","statusChangedAt":"%s"}
                                """.formatted(ACCOUNT_ID, now.toString()))));

        // Clean slate for the account under test
        deviceSessionRepository.findActiveByAccountId(ACCOUNT_ID)
                .forEach(s -> {
                    s.revoke(Instant.now(), RevokeReason.ADMIN_FORCED);
                    deviceSessionRepository.save(s);
                });
    }

    @Test
    @DisplayName("Two logins from distinct user-agents yield two sessions")
    void twoLoginsFromDistinctUserAgents_listsTwoSessions() throws Exception {
        login("agent/alpha");
        login("agent/beta");

        MvcResult list = mockMvc.perform(get("/api/accounts/me/sessions")
                        .header("X-Account-Id", ACCOUNT_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andReturn();

        JsonNode body = objectMapper.readTree(list.getResponse().getContentAsString());
        List<String> families = body.get("items").findValuesAsText("userAgentFamily");
        assertThat(families).hasSize(2);
    }

    @Test
    @DisplayName("Exceeding max-active-sessions evicts oldest and publishes revoke event")
    void eleventhLogin_evictsOldest_andPublishesOutboxEvent() throws Exception {
        for (int i = 1; i <= 11; i++) {
            login("agent/device-" + i);
        }

        List<DeviceSession> active = deviceSessionRepository.findActiveByAccountId(ACCOUNT_ID);
        assertThat(active).hasSize(10);

        // The outbox must contain at least one auth.session.revoked row for this account,
        // proving the eviction fired and the cascade wrote an event (scenario payload
        // detail is covered by unit tests; integration-level we only assert the row exists).
        long revokedEvents = outboxJpaRepository.findAll().stream()
                .filter(o -> "auth.session.revoked".equals(o.getEventType()))
                .filter(o -> ACCOUNT_ID.equals(o.getAggregateId()))
                .count();
        assertThat(revokedEvents).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("Revoking a single session invalidates the matching refresh token")
    void deleteSingleSession_rejectsSubsequentRefresh() throws Exception {
        String refreshTokenA = loginAndExtractRefreshToken("agent/delete-me");
        String refreshTokenB = loginAndExtractRefreshToken("agent/keep");

        List<DeviceSession> sessions = deviceSessionRepository.findActiveByAccountId(ACCOUNT_ID);
        assertThat(sessions).hasSize(2);
        String deviceIdToDrop = deviceIdOf(sessions, "delete-me");
        String deviceIdToKeep = deviceIdOf(sessions, "keep");

        mockMvc.perform(delete("/api/accounts/me/sessions/" + deviceIdToDrop)
                        .header("X-Account-Id", ACCOUNT_ID))
                .andExpect(status().isNoContent());

        // TASK-MONO-393 — assert the scoping property in the DB, where Redis cannot lie about it.
        // The HTTP checks below cannot carry this on their own: the blacklist lookup is
        // deliberately fail-closed, so if Redis is unreachable EVERY refresh answers 401 — which
        // silently satisfies the "revoked -> 401" expectation for the wrong reason and then fails
        // the surviving one, making an infrastructure outage look like a cross-session token leak.
        // That is exactly what happened in PR #2515 and it cost a ticket to disprove.
        assertThat(refreshTokenRepository.findActiveJtisByDeviceId(deviceIdToDrop))
                .as("the revoked device's refresh token must be gone")
                .isEmpty();
        assertThat(refreshTokenRepository.findActiveJtisByDeviceId(deviceIdToKeep))
                .as("revoking one device must NOT touch the other device's refresh token")
                .hasSize(1);

        // Refresh with the dropped token -> 401
        mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshTokenA)))
                .andExpect(status().isUnauthorized());

        // Other token still works — and if it does not, say WHY.
        MvcResult surviving = mockMvc.perform(post("/api/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"refreshToken\":\"%s\"}".formatted(refreshTokenB)))
                .andReturn();
        int status = surviving.getResponse().getStatus();
        if (status != 200) {
            fail(explainSurvivingTokenRejection(status));
        }
    }

    /**
     * Names the cause of an unexpected rejection instead of letting the next reader infer a
     * security defect from a bare {@code expected:<200> but was:<401>} (TASK-MONO-393).
     */
    private String explainSurvivingTokenRejection(int status) {
        if (!redisReachable()) {
            return ("The surviving device's refresh was rejected (%d), but Redis is UNREACHABLE. "
                    + "The blacklist check is fail-closed, so it denies every refresh while Redis is "
                    + "down — this is NOT a cross-session revoke leak. The DB assertions above already "
                    + "proved the other device's token survived. Cause is the Testcontainers stack, not "
                    + "auth-service: see TASK-MONO-393 (the iam lane boots seven stacks on one runner).")
                    .formatted(status);
        }
        return ("The surviving device's refresh was rejected (%d) while Redis is REACHABLE. "
                + "The DB assertions above passed, so the token row is still active — meaning the "
                + "rejection came from the token pipeline itself (blacklist entry, bulk-invalidation "
                + "marker, tenant mismatch or reuse detection), not from session scoping. "
                + "This one is real; do not dismiss it as flake.").formatted(status);
    }

    private boolean redisReachable() {
        try {
            redisTemplate.hasKey("mono-393:liveness-probe");
            return true;
        } catch (DataAccessException e) {
            return false;
        }
    }

    private static String deviceIdOf(List<DeviceSession> sessions, String userAgentNeedle) {
        return sessions.stream()
                .filter(s -> s.getUserAgent() != null && s.getUserAgent().contains(userAgentNeedle))
                .findFirst().orElseThrow()
                .getDeviceId();
    }

    @Test
    @DisplayName("Bulk revoke keeps only the caller's current session")
    void bulkRevoke_keepsCurrentSessionOnly() throws Exception {
        login("agent/one");
        login("agent/two");
        login("agent/current");

        String currentDeviceId = deviceSessionRepository.findActiveByAccountId(ACCOUNT_ID)
                .stream()
                .filter(s -> s.getUserAgent() != null && s.getUserAgent().contains("current"))
                .findFirst().orElseThrow()
                .getDeviceId();

        MvcResult result = mockMvc.perform(delete("/api/accounts/me/sessions")
                        .header("X-Account-Id", ACCOUNT_ID)
                        .header("X-Device-Id", currentDeviceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.revokedCount").value(2))
                .andReturn();

        List<DeviceSession> remaining = deviceSessionRepository.findActiveByAccountId(ACCOUNT_ID);
        assertThat(remaining).hasSize(1);
        assertThat(remaining.get(0).getDeviceId()).isEqualTo(currentDeviceId);

        assertThat(result.getResponse().getContentAsString()).contains("\"revokedCount\":2");
    }

    // ----- helpers -----

    private MvcResult login(String userAgent) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                        .header("User-Agent", userAgent)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(TEST_EMAIL, TEST_PASSWORD)))
                .andExpect(status().isOk())
                .andReturn();
    }

    private String loginAndExtractRefreshToken(String userAgent) throws Exception {
        MvcResult res = login(userAgent);
        JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
        return body.get("refreshToken").asText();
    }
}
