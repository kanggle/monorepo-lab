package com.example.settlement;

import com.example.settlement.application.event.SettlementPeriodClosedEvent;
import com.example.settlement.application.port.SettlementEventPublisher;
import com.example.settlement.infrastructure.event.SettlementOutboxPublisher;
import com.example.settlement.infrastructure.persistence.SettlementOutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static java.time.Duration.ofSeconds;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trip integration test for the settlement-service outbox v2 relay
 * (TASK-BE-447). Proves: the write path persists a {@code settlement_outbox} row →
 * {@link SettlementOutboxPublisher} drains it to Kafka with the {@code eventId}/{@code eventType}
 * headers + preserved key ({@code periodId}) + value (byte-identical envelope) →
 * the row's {@code published_at} is set.
 *
 * <p><b>Verification note (TASK-BE-447 AC-7):</b> settlement-service has no
 * Testcontainers CI lane (only order/payment do), so this {@code @Tag("integration")}
 * test is excluded from the Docker-free {@code :test} task and does not run in CI
 * today. Authored for compile-time verification + activation if a lane is added.
 */
@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = "settlement.period.closed")
@DisplayName("settlement outbox v2 relay 통합 테스트")
class SettlementOutboxRelayIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("settlement_db")
            .withUsername("settlement_user")
            .withPassword("settlement_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private SettlementEventPublisher writePath;

    @Autowired
    private SettlementOutboxPublisher relay;

    @Autowired
    private SettlementOutboxRepository outboxRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmbeddedKafkaBroker broker;

    @Test
    @DisplayName("write path 가 settlement_outbox 행을 적재하고 relay 가 헤더+키+값 보존하여 발행한다")
    void writeThenRelay_publishesWithHeadersAndPreservedKeyValue_thenMarksPublished() throws Exception {
        String eventId = UUID.randomUUID().toString();
        SettlementPeriodClosedEvent.Payload payload = new SettlementPeriodClosedEvent.Payload(
                "period-it-1", "ecommerce", "2026-06-01T00:00:00Z", "2026-07-01T00:00:00Z",
                "2026-07-01T00:00:05Z", 1,
                List.of(new SettlementPeriodClosedEvent.PayoutLine("seller-it-1", 9000L, 1000L, 3)));
        SettlementPeriodClosedEvent event = SettlementPeriodClosedEvent.of(
                eventId, "ecommerce", Instant.parse("2026-07-01T00:00:05Z"), payload);
        String expectedPayload = objectMapper.writeValueAsString(event);

        writePath.publishPeriodClosed(event);
        assertThat(outboxRepository.countByPublishedAtIsNull()).isEqualTo(1L);

        Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<String, String>(
                KafkaTestUtils.consumerProps("settlement-it", "true", broker),
                new org.apache.kafka.common.serialization.StringDeserializer(),
                new org.apache.kafka.common.serialization.StringDeserializer())
                .createConsumer();
        broker.consumeFromAnEmbeddedTopic(consumer, "settlement.period.closed");

        relay.publishPending();

        ConsumerRecord<String, String> record =
                KafkaTestUtils.getSingleRecord(consumer, "settlement.period.closed", ofSeconds(10));
        assertThat(record.key()).isEqualTo("period-it-1");
        assertThat(record.value()).isEqualTo(expectedPayload);
        assertThat(new String(record.headers().lastHeader("eventId").value())).isEqualTo(eventId);
        assertThat(new String(record.headers().lastHeader("eventType").value()))
                .isEqualTo(SettlementPeriodClosedEvent.EVENT_TYPE);
        consumer.close();

        assertThat(outboxRepository.countByPublishedAtIsNull()).isZero();
    }
}
