package com.example.membership.integration;

import com.example.testsupport.integration.AbstractIntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.example.membership.integration.MembershipJwtTestSupport.bearer;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration test for the Activate Subscription use-case end-to-end.
 * Boots membership-service against real MySQL + Kafka + Redis (Testcontainers)
 * and a WireMock stub for account-service.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Import(MembershipJwtTestSupport.JwtDecoderConfig.class)
@DisplayName("ActivateSubscription integration — full stack")
class ActivateSubscriptionIntegrationTest extends AbstractIntegrationTest {

    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    static WireMockServer wireMock;

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
    static void overrideProps(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("membership.account-service.base-url", wireMock::baseUrl);
    }

    @Autowired
    MockMvc mockMvc;

    @Autowired
    JdbcTemplate jdbcTemplate;

    @BeforeEach
    void reset() {
        wireMock.resetAll();
        // Clean subscriptions so each test is isolated.
        // subscription_status_history has an append-only trigger blocking DELETE —
        // bypass via TRUNCATE which does not fire row-level triggers.
        jdbcTemplate.execute("TRUNCATE TABLE subscription_status_history");
        jdbcTemplate.update("DELETE FROM subscriptions");
        jdbcTemplate.update("DELETE FROM outbox");
    }

    private void stubAccountStatus(String accountId, String statusValue) {
        wireMock.stubFor(WireMock.get(urlPathMatching("/internal/accounts/" + accountId + "/status"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"accountId\":\"" + accountId + "\",\"status\":\"" + statusValue + "\"}")));
    }

    @Test
    @DisplayName("ACTIVE account + fresh key → 201 Created and outbox event emitted")
    void activeAccount_returns201AndEmitsEvent() throws Exception {
        String accountId = UUID.randomUUID().toString();
        String idem = "idem-" + UUID.randomUUID();
        stubAccountStatus(accountId, "ACTIVE");

        mockMvc.perform(post("/api/membership/subscriptions")
                        .header("Authorization", bearer(accountId, java.util.List.of("FAN")))
                        .header("X-Account-Id", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planLevel\":\"FAN_CLUB\",\"idempotencyKey\":\"" + idem + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.planLevel").value("FAN_CLUB"));

        await().atMost(java.time.Duration.ofSeconds(5)).untilAsserted(() -> {
            List<Map<String, Object>> events = jdbcTemplate.queryForList(
                    "SELECT event_type FROM outbox WHERE event_type = 'membership.subscription.activated'");
            assertThat(events).isNotEmpty();
        });
    }

    @Test
    @DisplayName("LOCKED account → 409 ACCOUNT_NOT_ELIGIBLE, no subscription row created")
    void lockedAccount_returns409() throws Exception {
        String accountId = UUID.randomUUID().toString();
        stubAccountStatus(accountId, "LOCKED");

        mockMvc.perform(post("/api/membership/subscriptions")
                        .header("Authorization", bearer(accountId, java.util.List.of("FAN")))
                        .header("X-Account-Id", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planLevel\":\"FAN_CLUB\",\"idempotencyKey\":\"idem-locked\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("ACCOUNT_NOT_ELIGIBLE"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subscriptions WHERE account_id = ?",
                Integer.class, accountId);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("account-service 503 → 503 ACCOUNT_STATUS_UNAVAILABLE (fail-closed)")
    void accountServiceUnavailable_returns503() throws Exception {
        String accountId = UUID.randomUUID().toString();
        wireMock.stubFor(WireMock.get(urlPathMatching("/internal/accounts/" + accountId + "/status"))
                .willReturn(aResponse().withStatus(503)));

        mockMvc.perform(post("/api/membership/subscriptions")
                        .header("Authorization", bearer(accountId, java.util.List.of("FAN")))
                        .header("X-Account-Id", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planLevel\":\"FAN_CLUB\",\"idempotencyKey\":\"idem-down\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("ACCOUNT_STATUS_UNAVAILABLE"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subscriptions WHERE account_id = ?",
                Integer.class, accountId);
        assertThat(count).isEqualTo(0);
    }

    @Test
    @DisplayName("Idempotency-Key replay → 200 OK and no duplicate subscription")
    void idempotentReplay_returns200() throws Exception {
        String accountId = UUID.randomUUID().toString();
        String idem = "idem-replay-" + UUID.randomUUID();
        stubAccountStatus(accountId, "ACTIVE");

        // First: 201 Created
        mockMvc.perform(post("/api/membership/subscriptions")
                        .header("Authorization", bearer(accountId, java.util.List.of("FAN")))
                        .header("X-Account-Id", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planLevel\":\"FAN_CLUB\",\"idempotencyKey\":\"" + idem + "\"}"))
                .andExpect(status().isCreated());

        // Replay with same key: 200 OK, same subscription
        mockMvc.perform(post("/api/membership/subscriptions")
                        .header("Authorization", bearer(accountId, java.util.List.of("FAN")))
                        .header("X-Account-Id", accountId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"planLevel\":\"FAN_CLUB\",\"idempotencyKey\":\"" + idem + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM subscriptions WHERE account_id = ?",
                Integer.class, accountId);
        assertThat(count).isEqualTo(1);
    }
}
