package com.example.security.integration;

import com.example.security.infrastructure.persistence.LoginHistoryJpaEntity;
import com.example.security.infrastructure.persistence.LoginHistoryJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class SecurityServiceIntegrationTest extends AbstractIntegrationTest {

    // MySQL + Kafka inherited from AbstractIntegrationTest (TASK-BE-076/078).
    // Kafka image version is pinned to cp-kafka:7.6.0 there; no local override.
    // Redis remains service-specific.
    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        // MySQL + Kafka registered by AbstractIntegrationTest.
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("spring.data.redis.password", () -> "");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration");
    }

    @Autowired
    private LoginHistoryJpaRepository loginHistoryJpaRepository;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    private KafkaTemplate<String, String> kafkaTemplate;

    @BeforeEach
    void setUp() {
        Map<String, Object> producerProps = new HashMap<>();
        producerProps.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        producerProps.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        producerProps.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        DefaultKafkaProducerFactory<String, String> pf = new DefaultKafkaProducerFactory<>(producerProps);
        kafkaTemplate = new KafkaTemplate<>(pf);
    }

    @Test
    @Order(1)
    @DisplayName("Consumes auth.login.succeeded event and stores in login_history")
    void consumeLoginSucceededEvent() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String accountId = "acc-integration-001";
        String eventJson = buildLoginSucceededEvent(eventId, accountId);

        ProducerRecord<String, String> record = new ProducerRecord<>("auth.login.succeeded", accountId, eventJson);
        record.headers().add(new RecordHeader("traceparent", "00-abcdef1234567890abcdef1234567890-1234567890abcdef-01".getBytes(StandardCharsets.UTF_8)));
        kafkaTemplate.send(record);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<LoginHistoryJpaEntity> found = loginHistoryJpaRepository.findAll().stream()
                    .filter(e -> e.getEventId().equals(eventId))
                    .findFirst();
            assertThat(found).isPresent();
            assertThat(found.get().getOutcome()).isEqualTo("SUCCESS");
            assertThat(found.get().getAccountId()).isEqualTo(accountId);
            assertThat(found.get().getGeoCountry()).isEqualTo("KR");
        });
    }

    @Test
    @Order(2)
    @DisplayName("Duplicate eventId is processed only once (idempotent)")
    void duplicateEventProcessedOnce() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String accountId = "acc-dedup-001";
        String eventJson = buildLoginSucceededEvent(eventId, accountId);

        kafkaTemplate.send("auth.login.succeeded", accountId, eventJson);
        kafkaTemplate.send("auth.login.succeeded", accountId, eventJson);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            long count = loginHistoryJpaRepository.findAll().stream()
                    .filter(e -> e.getEventId().equals(eventId))
                    .count();
            assertThat(count).isEqualTo(1);
        });
    }

    @Test
    @Order(3)
    @DisplayName("Query API returns paginated login history with IP masking")
    void queryLoginHistory() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String accountId = "acc-query-001";
        String eventJson = buildLoginSucceededEvent(eventId, accountId);

        kafkaTemplate.send("auth.login.succeeded", accountId, eventJson);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            assertThat(loginHistoryJpaRepository.findAll().stream()
                    .anyMatch(e -> e.getEventId().equals(eventId))).isTrue();
        });

        mockMvc.perform(get("/internal/security/login-history")
                        .header("X-Internal-Token", "test-internal-token")
                        .param("accountId", accountId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].accountId").value(accountId))
                .andExpect(jsonPath("$.content[0].ipMasked").value("192.168.1.***"))
                .andExpect(jsonPath("$.totalElements").isNumber());
    }

    @Test
    @Order(4)
    @DisplayName("Suspicious events endpoint returns empty placeholder")
    void suspiciousEventsPlaceholder() throws Exception {
        mockMvc.perform(get("/internal/security/suspicious-events")
                        .header("X-Internal-Token", "test-internal-token")
                        .param("accountId", "acc-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @Order(5)
    @DisplayName("Consumes auth.login.failed event with FAILURE outcome")
    void consumeLoginFailedEvent() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String accountId = "acc-failed-001";
        String eventJson = buildLoginFailedEvent(eventId, accountId, "CREDENTIALS_INVALID");

        kafkaTemplate.send("auth.login.failed", accountId, eventJson);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<LoginHistoryJpaEntity> found = loginHistoryJpaRepository.findAll().stream()
                    .filter(e -> e.getEventId().equals(eventId))
                    .findFirst();
            assertThat(found).isPresent();
            assertThat(found.get().getOutcome()).isEqualTo("FAILURE");
        });
    }

    @Test
    @Order(6)
    @DisplayName("Consumes auth.login.failed event with RATE_LIMITED outcome")
    void consumeRateLimitedEvent() throws Exception {
        String eventId = UUID.randomUUID().toString();
        String accountId = "acc-ratelimited-001";
        String eventJson = buildLoginFailedEvent(eventId, accountId, "RATE_LIMITED");

        kafkaTemplate.send("auth.login.failed", accountId, eventJson);

        await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
            Optional<LoginHistoryJpaEntity> found = loginHistoryJpaRepository.findAll().stream()
                    .filter(e -> e.getEventId().equals(eventId))
                    .findFirst();
            assertThat(found).isPresent();
            assertThat(found.get().getOutcome()).isEqualTo("RATE_LIMITED");
        });
    }

    private String buildLoginSucceededEvent(String eventId, String accountId) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "auth.login.succeeded",
                  "source": "auth-service",
                  "occurredAt": "%s",
                  "schemaVersion": 1,
                  "partitionKey": "%s",
                  "payload": {
                    "accountId": "%s",
                    "ipMasked": "192.168.1.***",
                    "userAgentFamily": "Chrome 120",
                    "deviceFingerprint": "abcdef123456789012345678",
                    "geoCountry": "KR",
                    "sessionJti": "jti-001",
                    "timestamp": "%s"
                  }
                }
                """.formatted(eventId, Instant.now(), accountId, accountId, Instant.now());
    }

    private String buildLoginFailedEvent(String eventId, String accountId, String failureReason) {
        return """
                {
                  "eventId": "%s",
                  "eventType": "auth.login.failed",
                  "source": "auth-service",
                  "occurredAt": "%s",
                  "schemaVersion": 1,
                  "partitionKey": "%s",
                  "payload": {
                    "accountId": "%s",
                    "emailHash": "hash123",
                    "failureReason": "%s",
                    "failCount": 3,
                    "ipMasked": "10.0.0.***",
                    "userAgentFamily": "Safari 17",
                    "deviceFingerprint": "xyz789",
                    "geoCountry": "US",
                    "timestamp": "%s"
                  }
                }
                """.formatted(eventId, Instant.now(), accountId, accountId, failureReason, Instant.now());
    }
}
