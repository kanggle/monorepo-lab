package com.example.security.integration;

import com.example.messaging.outbox.OutboxJpaEntity;
import com.example.messaging.outbox.OutboxJpaRepository;
import com.example.security.domain.Tenants;
import com.example.security.infrastructure.persistence.SuspiciousEventJpaEntity;
import com.example.security.infrastructure.persistence.SuspiciousEventJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * End-to-end detection pipeline test:
 * auth.login.failed (velocity >= threshold) → AUTO_LOCK decision →
 * account-service /internal/accounts/{id}/lock call (WireMock) →
 * suspicious_events row + outbox row for security.auto.lock.triggered.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
class DetectionE2EIntegrationTest extends AbstractIntegrationTest {

    // MySQL + Kafka inherited from AbstractIntegrationTest (TASK-BE-076).
    // Redis remains service-specific.
    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static WireMockServer wireMockServer;

    static {
        wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
        wireMockServer.start();
    }

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
        // Lower velocity threshold so 3 failed logins trigger AUTO_LOCK in-test.
        registry.add("security.detection.velocity.threshold", () -> "3");
        registry.add("security.detection.velocity.window-seconds", () -> "3600");
        // Point auto-lock client at WireMock, fast timeouts.
        registry.add("security.detection.auto-lock.account-service-base-url",
                () -> "http://localhost:" + wireMockServer.port());
        // TASK-BE-318: the auto-lock client now fetches a GAP client_credentials Bearer token.
        // Point the token endpoint at the same WireMock (stubbed in setUp) so the lock call
        // authenticates without a real auth-service.
        registry.add("gap.internal-client.token-uri",
                () -> "http://localhost:" + wireMockServer.port() + "/oauth2/token");
        registry.add("security.detection.auto-lock.max-attempts", () -> "1");
        registry.add("security.detection.auto-lock.initial-backoff-ms", () -> "50");
        registry.add("security.detection.auto-lock.connect-timeout-ms", () -> "2000");
        registry.add("security.detection.auto-lock.read-timeout-ms", () -> "2000");
        // Speed up outbox polling for event-visibility assertions (if needed).
        registry.add("outbox.polling.interval-ms", () -> "1000");
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @Autowired private SuspiciousEventJpaRepository suspiciousEventJpaRepository;
    @Autowired private OutboxJpaRepository outboxJpaRepository;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private KafkaListenerEndpointRegistry listenerRegistry;

    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        // TASK-BE-318: stub the GAP client_credentials token endpoint so the auto-lock client can
        // obtain a Bearer token. resetAll() clears stubs, so (re)register it each test.
        wireMockServer.stubFor(post(urlEqualTo("/oauth2/token"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"access_token\":\"test-jwt\",\"expires_in\":300,\"token_type\":\"Bearer\"}")));
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        kafkaTemplate = new KafkaTemplate<>(new DefaultKafkaProducerFactory<>(producerProps));
        // TASK-MONO-046-3 Phase 7: wait for partition assignment before producing.
        listenerRegistry.getListenerContainers()
                .forEach(c -> ContainerTestUtils.waitForAssignment(c, 1));
    }

    @Test
    @DisplayName("10x auth.login.failed events → VelocityRule AUTO_LOCK → account-service lock called, suspicious_events row + outbox event")
    void velocityTriggersAutoLockE2E() {
        // VARCHAR(36) — keep this exactly UUID-shaped (TASK-MONO-046-8a fix
        // for "Data truncation: Data too long for column 'account_id'").
        String accountId = UUID.randomUUID().toString();

        // Stub account-service lock endpoint
        wireMockServer.stubFor(post(urlEqualTo("/internal/accounts/" + accountId + "/lock"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody(String.format(
                                "{\"accountId\":\"%s\",\"previousStatus\":\"ACTIVE\",\"currentStatus\":\"LOCKED\",\"lockedAt\":\"%s\"}",
                                accountId, Instant.now()))));

        // Send 10 failed login events — well above the velocity threshold (3 for this test).
        for (int i = 0; i < 10; i++) {
            String eventId = UUID.randomUUID().toString();
            String json = buildLoginFailedEvent(eventId, accountId);
            kafkaTemplate.send("auth.login.failed", accountId, json);
        }

        // Wait until a SuspiciousEvent with AUTO_LOCK action is persisted for this account.
        await().atMost(30, TimeUnit.SECONDS).untilAsserted(() -> {
            List<SuspiciousEventJpaEntity> rows = suspiciousEventJpaRepository
                    .findByTenantIdAndAccountIdAndDetectedAtBetweenOrderByDetectedAtDesc(
                            Tenants.DEFAULT_TENANT_ID, accountId,
                            Instant.now().minusSeconds(600), Instant.now().plusSeconds(60));
            assertThat(rows).as("suspicious_events row with AUTO_LOCK").isNotEmpty();
            SuspiciousEventJpaEntity row = rows.get(0);
            assertThat(row.getActionTaken()).isEqualTo("AUTO_LOCK");
            assertThat(row.getRiskScore()).isGreaterThanOrEqualTo(80);
            assertThat(row.getLockRequestResult()).isEqualTo("SUCCESS");
        });

        // WireMock received the lock call with Idempotency-Key header set to the suspicious event id.
        List<SuspiciousEventJpaEntity> rows = suspiciousEventJpaRepository
                .findByTenantIdAndAccountIdAndDetectedAtBetweenOrderByDetectedAtDesc(
                        Tenants.DEFAULT_TENANT_ID, accountId,
                        Instant.now().minusSeconds(600), Instant.now().plusSeconds(60));
        String suspiciousEventId = rows.get(0).getId();

        wireMockServer.verify(
                RequestPatternBuilder.newRequestPattern(
                                com.github.tomakehurst.wiremock.http.RequestMethod.POST,
                                urlEqualTo("/internal/accounts/" + accountId + "/lock"))
                        .withHeader("Idempotency-Key", equalTo(suspiciousEventId))
                        .withHeader("Content-Type", equalTo("application/json")));

        // Outbox row for security.auto.lock.triggered event with normalized lockRequestResult=SUCCESS.
        // The burst publishes 10 events → up to 10 auto-lock-triggered outbox rows
        // (one per suspicious event); pick the row whose suspiciousEventId matches
        // the one we just located in the suspicious_events table — outbox findAll()
        // ordering is not guaranteed.
        await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
            OutboxJpaEntity matching = outboxJpaRepository.findAll().stream()
                    .filter(e -> "security.auto.lock.triggered".equals(e.getEventType()))
                    .filter(e -> accountId.equals(e.getAggregateId()))
                    .filter(e -> {
                        try {
                            JsonNode env = objectMapper.readTree(e.getPayload());
                            return suspiciousEventId.equals(env.path("payload").path("suspiciousEventId").asText());
                        } catch (Exception ex) {
                            return false;
                        }
                    })
                    .findFirst()
                    .orElse(null);
            assertThat(matching).as("outbox row for security.auto.lock.triggered with suspiciousEventId=%s",
                    suspiciousEventId).isNotNull();
            JsonNode envelope = objectMapper.readTree(matching.getPayload());
            assertThat(envelope.path("eventType").asText()).isEqualTo("security.auto.lock.triggered");
            assertThat(envelope.path("source").asText()).isEqualTo("security-service");
            JsonNode payload = envelope.path("payload");
            assertThat(payload.path("accountId").asText()).isEqualTo(accountId);
            assertThat(payload.path("suspiciousEventId").asText()).isEqualTo(suspiciousEventId);
            // The event payload and DB column must use the same normalized vocabulary.
            assertThat(payload.path("lockRequestResult").asText()).isEqualTo("SUCCESS");
        });
    }

    private String buildLoginFailedEvent(String eventId, String accountId) {
        // TASK-BE-248 Phase 2a: tenantId in envelope is required — events without it
        // are routed to .dlq by AbstractAuthEventConsumer (MissingTenantIdException).
        return """
                {
                  "eventId": "%s",
                  "eventType": "auth.login.failed",
                  "tenantId": "%s",
                  "source": "auth-service",
                  "occurredAt": "%s",
                  "schemaVersion": 1,
                  "partitionKey": "%s",
                  "payload": {
                    "accountId": "%s",
                    "emailHash": "hash-e2e",
                    "failureReason": "CREDENTIALS_INVALID",
                    "failCount": 1,
                    "ipMasked": "10.0.0.***",
                    "userAgentFamily": "Chrome 120",
                    "deviceFingerprint": "dev-e2e-velocity",
                    "geoCountry": "KR",
                    "timestamp": "%s"
                  }
                }
                """.formatted(eventId, Tenants.DEFAULT_TENANT_ID, Instant.now(), accountId, accountId, Instant.now());
    }
}
