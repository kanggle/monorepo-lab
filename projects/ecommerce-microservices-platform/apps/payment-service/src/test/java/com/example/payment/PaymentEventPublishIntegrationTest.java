package com.example.payment;

import com.example.payment.adapter.in.event.OrderPlacedEventConsumer;
import com.example.payment.application.port.out.PaymentGatewayConfirmResult;
import com.example.payment.application.port.out.PaymentGatewayPort;
import com.example.payment.application.service.PaymentConfirmService;
import com.example.payment.application.service.PaymentRefundService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

/**
 * Full round-trip integration test for ADR-006 Scenario A (TASK-BE-136):
 * payment-service use-case commit → outbox row PENDING → polling relay
 * publishes → Kafka consumer receives the envelope unchanged.
 *
 * <p>Asserts the transactional outbox contract end-to-end: writer
 * persists inside the use-case transaction, relay polls within the
 * configured interval, and the published envelope matches the contract
 * defined in {@code specs/contracts/events/payment-events.md}.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        // TASK-BE-449 outbox v2: relay enabled (gate kept), v2 timing knobs fast so the
        // @Scheduled poller drains within the 15 s consumer deadline below.
        "payment.outbox.poll-ms=300",
        "payment.outbox.initial-delay-ms=0",
        "outbox.polling.enabled=true"
})
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {
        "payment.payment.completed",
        "payment.payment.refunded"
})
@DisplayName("Payment 이벤트 Outbox round-trip 통합 테스트 (TASK-BE-136 / ADR-006 Scenario A)")
class PaymentEventPublishIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("payment_db")
            .withUsername("payment_user")
            .withPassword("payment_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private OrderPlacedEventConsumer orderPlacedEventConsumer;

    @Autowired
    private PaymentConfirmService paymentConfirmService;

    @Autowired
    private PaymentRefundService paymentRefundService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @MockitoBean
    private PaymentGatewayPort paymentGateway;

    @BeforeEach
    void stubPaymentGateway() {
        given(paymentGateway.confirmPayment(anyString(), anyString(), anyLong()))
                .willReturn(new PaymentGatewayConfirmResult("CARD", "https://receipt.test/mock"));
    }

    private String buildOrderPlacedJson(String orderId, String userId, long totalPrice) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "OrderPlaced",
                "occurredAt", "2026-03-23T00:00:00",
                "source", "order-service",
                "payload", Map.of(
                        "orderId", orderId,
                        "userId", userId,
                        "totalPrice", totalPrice,
                        "items", List.of()
                )
        ));
    }

    @Test
    @DisplayName("결제 확정 트랜잭션 커밋 시 outbox 에 PaymentCompleted 행이 PENDING 으로 저장된다")
    void confirmPayment_afterCommit_outboxEntryPersisted() throws Exception {
        String orderId = "order-" + System.nanoTime();
        String userId = "user-" + System.nanoTime();

        orderPlacedEventConsumer.onMessage(buildOrderPlacedJson(orderId, userId, 30000L));
        paymentConfirmService.confirm(userId, "pk_test_" + orderId, orderId, 30000L);

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM payment_outbox WHERE event_type = 'PaymentCompleted' AND payload LIKE ?",
                "%" + orderId + "%");

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("aggregate_type")).isEqualTo("Payment");
        assertThat(rows.get(0).get("aggregate_id")).isNotNull();
    }

    @Test
    @DisplayName("폴링 relay 가 outbox PENDING → PUBLISHED 로 전이시키고 Kafka 토픽에 envelope 을 발행한다")
    void pollingRelay_publishesToKafkaAndMarksRowPublished() throws Exception {
        String orderId = "order-poll-" + System.nanoTime();
        String userId = "user-poll-" + System.nanoTime();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "payment-event-it-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(consumerProps,
                new org.apache.kafka.common.serialization.StringDeserializer(),
                new org.apache.kafka.common.serialization.StringDeserializer()).createConsumer()) {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, "payment.payment.completed");

            orderPlacedEventConsumer.onMessage(buildOrderPlacedJson(orderId, userId, 50000L));
            paymentConfirmService.confirm(userId, "pk_test_" + orderId, orderId, 50000L);

            ConsumerRecord<String, String> record = pollForRecord(consumer, orderId);

            assertThat(record).as("Kafka 토픽에서 PaymentCompleted 이벤트를 수신해야 한다").isNotNull();
            JsonNode envelope = objectMapper.readTree(record.value());
            assertThat(envelope.get("event_type").asText()).isEqualTo("PaymentCompleted");
            assertThat(envelope.get("source").asText()).isEqualTo("payment-service");
            assertThat(envelope.get("payload").get("orderId").asText()).isEqualTo(orderId);
            assertThat(envelope.get("payload").get("userId").asText()).isEqualTo(userId);
            assertThat(envelope.get("payload").get("amount").asLong()).isEqualTo(50000L);
            assertThat(record.key()).isEqualTo(envelope.get("payload").get("paymentId").asText());
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM payment_outbox WHERE event_type = 'PaymentCompleted' AND payload LIKE ?",
                "%" + orderId + "%");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("published_at")).isNotNull(); // v2: no status column; published_at marks drained
    }

    @Test
    @DisplayName("폴링 relay 가 PaymentRefunded outbox row 도 PUBLISHED 로 전이시키고 payment.payment.refunded 토픽에 envelope 을 발행한다 (TASK-BE-137 W3)")
    void pollingRelay_refundedRoundTrip_publishesToKafkaAndMarksRowPublished() throws Exception {
        String orderId = "order-refund-" + System.nanoTime();
        String userId = "user-refund-" + System.nanoTime();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "payment-event-refund-it-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(consumerProps,
                new org.apache.kafka.common.serialization.StringDeserializer(),
                new org.apache.kafka.common.serialization.StringDeserializer()).createConsumer()) {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, "payment.payment.refunded");

            // commit chain: OrderPlaced → PENDING Payment → COMPLETED → REFUNDED
            orderPlacedEventConsumer.onMessage(buildOrderPlacedJson(orderId, userId, 75000L));
            paymentConfirmService.confirm(userId, "pk_test_" + orderId, orderId, 75000L);
            paymentRefundService.refundPayment(orderId);

            ConsumerRecord<String, String> record = pollForRecord(consumer, orderId);

            assertThat(record).as("Kafka 토픽에서 PaymentRefunded 이벤트를 수신해야 한다").isNotNull();
            JsonNode envelope = objectMapper.readTree(record.value());
            assertThat(envelope.get("event_type").asText()).isEqualTo("PaymentRefunded");
            assertThat(envelope.get("source").asText()).isEqualTo("payment-service");
            assertThat(envelope.get("payload").get("orderId").asText()).isEqualTo(orderId);
            assertThat(envelope.get("payload").get("userId").asText()).isEqualTo(userId);
            assertThat(envelope.get("payload").get("amount").asLong()).isEqualTo(75000L);
            assertThat(envelope.get("payload").get("refundedAt").asText()).isNotBlank();
            assertThat(record.key()).isEqualTo(envelope.get("payload").get("paymentId").asText());
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM payment_outbox WHERE event_type = 'PaymentRefunded' AND payload LIKE ?",
                "%" + orderId + "%");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("aggregate_type")).isEqualTo("Payment");
        assertThat(rows.get(0).get("published_at")).isNotNull(); // v2: no status column; published_at marks drained
    }

    private ConsumerRecord<String, String> pollForRecord(Consumer<String, String> consumer, String orderIdMarker) {
        long deadline = System.currentTimeMillis() + 15_000L;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> record : records) {
                if (record.value() != null && record.value().contains(orderIdMarker)) {
                    return record;
                }
            }
        }
        return null;
    }
}
