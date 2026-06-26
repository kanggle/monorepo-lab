package com.example.finance.account.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.example.finance.account.application.AccountApplicationService;
import com.example.finance.account.application.ActorContext;
import com.example.finance.account.application.command.OpenAccountCommand;
import com.example.finance.account.application.view.AccountView;
import com.example.finance.account.infrastructure.outbox.AccountOutboxJpaEntity;
import com.example.finance.account.infrastructure.outbox.AccountOutboxJpaRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Outbox v2 round-trip integration test (TASK-FIN-BE-045 — the authoritative
 * relay gate, AC-1/AC-2/AC-3/AC-5). Testcontainers MySQL + real Kafka.
 *
 * <p>Enables the {@link com.example.finance.account.infrastructure.outbox.AccountOutboxPublisher}
 * relay (off by default in the test profile), opens an account through the
 * application command boundary, then asserts:
 * <ol>
 *   <li>a pending {@code account_outbox} row is written in the open Tx with the
 *       preserved 7-field v1 envelope (AC-2/AC-5);</li>
 *   <li>the relay forwards it to {@code finance.account.opened.v1} and stamps
 *       {@code published_at} (AC-3/AC-5);</li>
 *   <li>the Kafka record carries the {@code eventId}/{@code eventType} headers,
 *       the record key = {@code aggregateId}, and the envelope is byte-faithful
 *       (AC-1/AC-2).</li>
 * </ol>
 */
class AccountOutboxRelayIntegrationTest extends AbstractAccountIntegrationTest {

    private static final ActorContext HOLDER =
            new ActorContext("user-1", TENANT_FINANCE, Set.of());
    private static final String TOPIC_ACCOUNT_OPENED = "finance.account.opened.v1";

    @Autowired
    AccountApplicationService service;
    @Autowired
    AccountOutboxJpaRepository outboxJpa;
    @Autowired
    ObjectMapper objectMapper;

    @DynamicPropertySource
    static void enableRelay(DynamicPropertyRegistry registry) {
        registry.add("account.outbox.polling.enabled", () -> "true");
        registry.add("account.outbox.polling-interval-ms", () -> "300");
        registry.add("account.outbox.initial-delay-ms", () -> "300");
    }

    @Test
    @DisplayName("open account → account_outbox row (v1 envelope) → relay publishes finance.account.opened.v1 + marks published_at")
    void outboxRoundTripForAccountOpened() {
        AccountView opened = service.openAccount(new OpenAccountCommand(
                HOLDER, "cust-outbox-1", "KRW", "NONE"));
        String accountId = opened.accountId();

        // (1) a pending row exists for the opened account (written in the open Tx).
        AccountOutboxJpaEntity row = await().atMost(Duration.ofSeconds(20))
                .until(() -> outboxJpa.findAll().stream()
                                .filter(r -> "finance.account.opened".equals(r.getEventType())
                                        && accountId.equals(r.getAggregateId()))
                                .findFirst()
                                .orElse(null),
                        r -> r != null);

        // (1b) the persisted payload is the preserved 7-field v1 envelope.
        JsonNode env = readTree(row.getPayload());
        assertThat(env.fieldNames()).toIterable().containsExactly(
                "eventId", "eventType", "source", "occurredAt", "schemaVersion",
                "partitionKey", "payload");
        assertThat(env.get("eventId").asText()).isEqualTo(row.getId().toString());
        assertThat(env.get("source").asText()).isEqualTo("finance-platform-account-service");
        assertThat(env.get("schemaVersion").asInt()).isEqualTo(1);
        assertThat(env.get("payload").get("accountId").asText()).isEqualTo(accountId);

        // (2) the relay publishes the record + (3) it carries headers/key/envelope.
        ConsumerRecord<String, String> record =
                awaitRecordForKey(TOPIC_ACCOUNT_OPENED, accountId, Duration.ofSeconds(30));
        assertThat(record.key()).isEqualTo(accountId);
        assertThat(header(record, "eventType")).isEqualTo("finance.account.opened");
        assertThat(header(record, "eventId")).isEqualTo(row.getId().toString());
        JsonNode wireEnv = readTree(record.value());
        assertThat(wireEnv.get("eventType").asText()).isEqualTo("finance.account.opened");
        assertThat(wireEnv.get("payload").get("accountId").asText()).isEqualTo(accountId);

        // (2b) the relay stamped published_at on the row.
        await().atMost(Duration.ofSeconds(20)).untilAsserted(() ->
                assertThat(outboxJpa.findById(row.getId()).orElseThrow().getPublishedAt())
                        .isNotNull());
    }

    // --- helpers ------------------------------------------------------------

    private ConsumerRecord<String, String> awaitRecordForKey(String topic, String key,
                                                             Duration timeout) {
        Properties props = new Properties();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "it-consumer-" + UUID.randomUUID());
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);

        long deadline = System.currentTimeMillis() + timeout.toMillis();
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(topic));
            while (System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
                for (ConsumerRecord<String, String> record : records) {
                    if (key.equals(record.key())) {
                        return record;
                    }
                }
            }
        }
        throw new AssertionError("No record with key " + key + " on " + topic
                + " within " + timeout);
    }

    private static String header(ConsumerRecord<String, String> record, String name) {
        Header h = record.headers().lastHeader(name);
        return h == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    private JsonNode readTree(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("bad JSON: " + json, e);
        }
    }
}
