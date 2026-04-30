package com.example.account.integration;

import com.example.account.domain.account.Account;
import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.status.AccountStatus;
import com.example.account.domain.tenant.TenantId;
import com.example.messaging.outbox.OutboxPollingScheduler;
import com.example.testsupport.integration.AbstractIntegrationTest;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Optional;
import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-247: integration test verifying that account-service handles a slow (4s) auth-service
 * response within the new 15s read-timeout window.
 *
 * <p>The test stubs auth-service to delay 4000ms before returning 201 Created. With the old 5s
 * timeout this sometimes succeeded (narrow window) but would fail for cold-start scenarios where
 * the delay is 5–10s. With the new 15s default the signup must always complete successfully.</p>
 *
 * <p>The test also verifies the idempotent 200 path: a stub that returns 200 (simulating auth-service
 * returning an idempotent response after a previous half-commit) must result in signup success.</p>
 */
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("TASK-BE-247: signup tolerates slow auth-service within new 15s timeout")
class SignupAuthServiceDelayIntegrationTest extends AbstractIntegrationTest {

    static WireMockServer wireMock;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("internal.api.token", () -> "test-internal-token");
        registry.add("account.auth-service.base-url",
                () -> "http://localhost:" + wireMock.port());
        // Use 15s read-timeout (the new default) — 4s delay stub must fit within this window
        registry.add("account.auth-service.connect-timeout-ms", () -> "3000");
        registry.add("account.auth-service.read-timeout-ms", () -> "15000");
    }

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(
                com.github.tomakehurst.wiremock.core.WireMockConfiguration
                        .wireMockConfig().dynamicPort());
        wireMock.start();
        WireMock.configureFor("localhost", wireMock.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMock != null) {
            wireMock.stop();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountRepository accountRepository;

    @MockitoBean
    @SuppressWarnings("rawtypes")
    private KafkaTemplate kafkaTemplate;

    @MockitoBean
    private OutboxPollingScheduler outboxPollingScheduler;

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @Test
    @DisplayName("auth-service responds with 4s delay → signup succeeds within 15s read-timeout")
    void signup_authServiceDelay4s_succeedsWithinNewTimeout() throws Exception {
        String email = "delay-test-" + UUID.randomUUID() + "@example.com";

        // Stub auth-service: delay 4s then return 201 Created
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/internal/auth/credentials"))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withFixedDelay(4_000)
                        .withBody("""
                                {"accountId":"acc-1","createdAt":"2026-04-30T10:00:00Z"}
                                """)));

        mockMvc.perform(post("/api/accounts/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password1!"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andExpect(jsonPath("$.accountId").exists());

        Optional<Account> saved = accountRepository.findByEmail(TenantId.FAN_PLATFORM, email);
        assertThat(saved).isPresent();
        assertThat(saved.get().getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    @DisplayName("auth-service returns 200 (idempotent) → signup treats it as success")
    void signup_authServiceIdempotent200_treatedAsSuccess() throws Exception {
        String email = "idempotent-test-" + UUID.randomUUID() + "@example.com";

        // Stub auth-service: return 200 OK (idempotent path — credential already existed)
        wireMock.stubFor(WireMock.post(urlPathEqualTo("/internal/auth/credentials"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("""
                                {"accountId":"acc-idempotent","createdAt":"2026-04-30T10:00:00Z"}
                                """)));

        mockMvc.perform(post("/api/accounts/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password1!"
                                }
                                """.formatted(email)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        Optional<Account> saved = accountRepository.findByEmail(TenantId.FAN_PLATFORM, email);
        assertThat(saved).isPresent();
    }
}
