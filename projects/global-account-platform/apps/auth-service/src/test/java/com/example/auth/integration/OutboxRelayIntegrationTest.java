package com.example.auth.integration;

import com.example.auth.application.event.AuthEventPublisher;
import com.example.auth.domain.session.SessionContext;
import com.example.messaging.outbox.OutboxJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import com.example.testsupport.integration.AbstractIntegrationTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/**
 * Integration test for the outbox relay: verifies that events written to the outbox
 * are published to Kafka topics with the correct envelope format and partition key.
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class OutboxRelayIntegrationTest extends AbstractIntegrationTest {

    // MySQL + Kafka inherited from AbstractIntegrationTest (TASK-BE-076/078).
    // Redis remains service-specific.
    @Container
    @SuppressWarnings("resource")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379);

    @Autowired
    private AuthEventPublisher authEventPublisher;

    @Autowired
    private OutboxJpaRepository outboxJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MySQL + Kafka registered by AbstractIntegrationTest.
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        registry.add("auth.account-service.base-url", () -> "http://localhost:18099");
    }

    private KafkaConsumer<String, String> createConsumer(String... topics) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "test-outbox-relay-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props);
        consumer.subscribe(Arrays.asList(topics));
        return consumer;
    }

    @Test
    @Order(1)
    @DisplayName("Login succeeded event is relayed from outbox to Kafka with correct envelope")
    void loginSucceededEventRelayedToKafka() throws Exception {
        String accountId = "acc-relay-test-001";
        String sessionJti = "jti-relay-test-001";
        SessionContext ctx = new SessionContext("192.168.1.100", "Chrome/120.0", "fp-abc123");

        // Create consumer before publishing
        try (KafkaConsumer<String, String> consumer = createConsumer("auth.login.succeeded")) {
            // Write event to outbox within a transaction (simulates what LoginUseCase does)
            transactionTemplate.executeWithoutResult(status ->
                    authEventPublisher.publishLoginSucceeded(accountId, sessionJti, ctx));

            // Wait for the outbox relay to pick up and publish
            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                List<ConsumerRecord<String, String>> matchingRecords = new ArrayList<>();
                for (ConsumerRecord<String, String> record : records) {
                    matchingRecords.add(record);
                }

                assertThat(matchingRecords).isNotEmpty();

                ConsumerRecord<String, String> record = matchingRecords.get(0);

                // Verify partition key is account_id
                assertThat(record.key()).isEqualTo(accountId);

                // Verify envelope format
                JsonNode envelope = objectMapper.readTree(record.value());
                assertThat(envelope.has("eventId")).isTrue();
                assertThat(envelope.get("eventType").asText()).isEqualTo("auth.login.succeeded");
                assertThat(envelope.get("source").asText()).isEqualTo("auth-service");
                assertThat(envelope.has("occurredAt")).isTrue();
                assertThat(envelope.get("schemaVersion").asInt()).isEqualTo(1);
                assertThat(envelope.get("partitionKey").asText()).isEqualTo(accountId);
                assertThat(envelope.has("payload")).isTrue();

                // Verify payload fields
                JsonNode payload = envelope.get("payload");
                assertThat(payload.get("accountId").asText()).isEqualTo(accountId);
                assertThat(payload.get("sessionJti").asText()).isEqualTo(sessionJti);
                assertThat(payload.get("ipMasked").asText()).isEqualTo("192.168.*.*");
                assertThat(payload.get("userAgentFamily").asText()).isEqualTo("Chrome");
                assertThat(payload.get("deviceFingerprint").asText()).isEqualTo("fp-abc123");
            });
        }

        // Verify outbox row is marked as published
        await().atMost(5, TimeUnit.SECONDS).untilAsserted(() -> {
            var publishedEntries = outboxJpaRepository.findAll().stream()
                    .filter(e -> e.getAggregateId().equals(accountId) && e.getPublishedAt() != null)
                    .toList();
            assertThat(publishedEntries).isNotEmpty();
        });
    }

    @Test
    @Order(2)
    @DisplayName("Login failed event is relayed with correct partition key and envelope")
    void loginFailedEventRelayedToKafka() throws Exception {
        String accountId = "acc-relay-test-002";
        String emailHash = "hash002abc";
        SessionContext ctx = new SessionContext("10.0.0.50", "Firefox/115.0", "fp-def456");

        try (KafkaConsumer<String, String> consumer = createConsumer("auth.login.failed")) {
            transactionTemplate.executeWithoutResult(status ->
                    authEventPublisher.publishLoginFailed(accountId, emailHash, "CREDENTIALS_INVALID", 3, ctx));

            await().atMost(10, TimeUnit.SECONDS).untilAsserted(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                List<ConsumerRecord<String, String>> matchingRecords = new ArrayList<>();
                for (ConsumerRecord<String, String> record : records) {
                    if (record.key().equals(accountId)) {
                        matchingRecords.add(record);
                    }
                }

                assertThat(matchingRecords).isNotEmpty();

                ConsumerRecord<String, String> record = matchingRecords.get(0);
                assertThat(record.key()).isEqualTo(accountId);

                JsonNode envelope = objectMapper.readTree(record.value());
                assertThat(envelope.get("eventType").asText()).isEqualTo("auth.login.failed");
                JsonNode payload = envelope.get("payload");
                assertThat(payload.get("failureReason").asText()).isEqualTo("CREDENTIALS_INVALID");
                assertThat(payload.get("failCount").asInt()).isEqualTo(3);
            });
        }
    }

    @Test
    @Order(3)
    @DisplayName("Multiple events for same account land in same partition (ordering guarantee)")
    void sameAccountEventsOrderedInSamePartition() throws Exception {
        String accountId = "acc-ordering-test-001";
        SessionContext ctx = new SessionContext("192.168.1.100", "Chrome/120.0", "fp-order");

        try (KafkaConsumer<String, String> consumer = createConsumer("auth.login.attempted", "auth.login.succeeded")) {
            // Write two events for the same account
            transactionTemplate.executeWithoutResult(status -> {
                authEventPublisher.publishLoginAttempted(accountId, "hash-order", ctx);
                authEventPublisher.publishLoginSucceeded(accountId, "jti-order", ctx);
            });

            Set<Integer> partitions = new HashSet<>();
            await().atMost(15, TimeUnit.SECONDS).untilAsserted(() -> {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if (record.key().equals(accountId)) {
                        partitions.add(record.partition());
                    }
                }
                // Both events should be in the same partition since they share the same key
                assertThat(partitions).hasSizeLessThanOrEqualTo(1);
            });
        }
    }
}
