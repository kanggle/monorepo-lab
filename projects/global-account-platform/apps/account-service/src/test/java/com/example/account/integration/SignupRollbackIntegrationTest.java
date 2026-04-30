package com.example.account.integration;

import com.example.account.domain.repository.AccountRepository;
import com.example.account.domain.tenant.TenantId;
import com.example.messaging.outbox.OutboxPollingScheduler;
import com.example.account.infrastructure.persistence.ProfileJpaRepository;
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

import java.util.UUID;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TASK-BE-065: verifies that when auth-service returns 5xx during signup,
 * account-service responds with 503 AUTH_SERVICE_UNAVAILABLE and the
 * {@code @Transactional} signup rolls back — no {@code accounts} / {@code profiles}
 * row remains for the submitted email.
 */
@SpringBootTest
@Testcontainers
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("TASK-BE-065: signup rollback on auth-service 5xx")
class SignupRollbackIntegrationTest extends AbstractIntegrationTest {

    static WireMockServer wireMock;

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("internal.api.token", () -> "test-internal-token");
        // Tighten retry timings so the 2-retry path finishes quickly in test
        registry.add("account.auth-service.connect-timeout-ms", () -> "1000");
        registry.add("account.auth-service.read-timeout-ms", () -> "1000");
        registry.add("account.auth-service.base-url",
                () -> "http://localhost:" + wireMock.port());
    }

    @BeforeAll
    static void startWireMock() {
        wireMock = new WireMockServer(com.github.tomakehurst.wiremock.core.WireMockConfiguration
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

    @Autowired
    private ProfileJpaRepository profileJpaRepository;

    // Signup writes only to the outbox table; stubbing Kafka avoids a ~50s producer
    // metadata lookup during context start (same rationale as AccountSignupIntegrationTest).
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
    @DisplayName("auth-service 500 응답 → 503 AUTH_SERVICE_UNAVAILABLE, accounts/profiles 롤백")
    void signup_authService5xx_rollsBackAndReturns503() throws Exception {
        String email = "rollback-" + UUID.randomUUID() + "@example.com";

        // Snapshot profile row count so we can prove this signup persisted no profile row
        long profileCountBefore = profileJpaRepository.count();

        wireMock.stubFor(WireMock.post(urlPathEqualTo("/internal/auth/credentials"))
                .willReturn(aResponse()
                        .withStatus(500)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"code\":\"INTERNAL_ERROR\"}")));

        mockMvc.perform(post("/api/accounts/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email": "%s",
                                  "password": "Password1!"
                                }
                                """.formatted(email)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("AUTH_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Authentication service is temporarily unavailable"))
                .andExpect(jsonPath("$.timestamp").exists());

        // @Transactional rollback proof:
        // 1) no account row persisted for the submitted email
        assertThat(accountRepository.findByEmail(TenantId.FAN_PLATFORM, email)).isEmpty();
        // 2) overall profile row count is unchanged — this signup added no profile row
        assertThat(profileJpaRepository.count()).isEqualTo(profileCountBefore);
    }
}
