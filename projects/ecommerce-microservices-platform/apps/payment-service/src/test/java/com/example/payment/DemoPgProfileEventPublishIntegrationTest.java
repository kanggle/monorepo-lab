package com.example.payment;

import com.example.payment.adapter.in.event.OrderPlacedEventConsumer;
import com.example.libs.payment.PaymentGatewayPort;
import com.example.libs.payment.toss.TossPaymentsAdapter;
import com.example.payment.application.service.PaymentConfirmService;
import com.example.payment.config.DemoPaymentGatewayConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The whole point of the {@code demo-pg} profile, asserted end-to-end (TASK-BE-572 AC-1):
 * <strong>the PG is fake and everything else is real.</strong>
 *
 * <p>Deliberately unlike its sibling {@code PaymentEventPublishIntegrationTest}, this test declares
 * <strong>no {@code @MockitoBean}</strong>. That sibling mocks {@link TossPaymentsAdapter} to make
 * the gateway approve — which means it proves the outbox works, not that any particular profile
 * wires it. Here the approving is done by the profile's own bean and the publishing by the real
 * {@code PaymentEventOutboxWriter} + relay, so a regression that re-introduced a no-op publisher
 * under this profile (the way {@code standalone} has one) turns this red. That regression is not
 * hypothetical: {@code standalone} is the obvious profile to reach for, and it is exactly the one
 * that silently swallows the event.
 */
@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "payment.outbox.poll-ms=300",
        "payment.outbox.initial-delay-ms=0",
        "outbox.polling.enabled=true"
})
@ActiveProfiles("demo-pg")
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1, topics = {
        "payment.payment.completed",
        "payment.payment.refunded"
})
@DisplayName("demo-pg 프로파일: mock 승인 + 실 Kafka 이벤트 (TASK-BE-572 AC-1)")
class DemoPgProfileEventPublishIntegrationTest {

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
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private ApplicationContext context;

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

    /**
     * The real Toss adapter must be ABSENT, not merely unused. Both beans implement the same
     * three lib ports, so if the exclusion on {@code PaymentGatewayConfig} were dropped the
     * context would not start at all — this asserts the intended shape rather than relying on
     * that crash to be interpreted correctly.
     */
    @Test
    @DisplayName("게이트웨이는 mock 하나뿐 — 실 Toss 어댑터 빈은 아예 없다")
    void onlyTheDemoGatewayIsRegistered() {
        assertThat(context.getBeanNamesForType(TossPaymentsAdapter.class)).isEmpty();
        assertThat(context.getBeansOfType(PaymentGatewayPort.class).values())
                .singleElement()
                .isInstanceOf(DemoPaymentGatewayConfig.DemoPaymentGateway.class);
    }

    @Test
    @DisplayName("mock 이 승인한 결제의 PaymentCompleted 가 실제 Kafka 토픽에 도착한다 (no-op 아님)")
    void mockApprovedPayment_reallyPublishesToKafka() throws Exception {
        String orderId = "order-demo-" + System.nanoTime();
        String userId = "user-demo-" + System.nanoTime();

        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
                "demo-pg-it-" + UUID.randomUUID(), "true", embeddedKafkaBroker);
        try (Consumer<String, String> consumer = new DefaultKafkaConsumerFactory<>(consumerProps,
                new org.apache.kafka.common.serialization.StringDeserializer(),
                new org.apache.kafka.common.serialization.StringDeserializer()).createConsumer()) {
            embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, "payment.payment.completed");

            orderPlacedEventConsumer.onMessage(buildOrderPlacedJson(orderId, userId, 50000L));
            // No stub anywhere: the demo gateway is what approves this.
            paymentConfirmService.confirm(userId, "demo_pk_" + orderId, orderId, 50000L);

            ConsumerRecord<String, String> record = pollForRecord(consumer, orderId);

            assertThat(record).as("demo-pg 에서도 PaymentCompleted 가 실제로 발행되어야 한다").isNotNull();
            JsonNode envelope = objectMapper.readTree(record.value());
            assertThat(envelope.get("event_type").asText()).isEqualTo("PaymentCompleted");
            assertThat(envelope.get("source").asText()).isEqualTo("payment-service");
            assertThat(envelope.get("payload").get("orderId").asText()).isEqualTo(orderId);
            assertThat(envelope.get("payload").get("amount").asLong()).isEqualTo(50000L);
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM payment_outbox WHERE event_type = 'PaymentCompleted' AND payload LIKE ?",
                "%" + orderId + "%");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("published_at")).isNotNull();
    }

    private ConsumerRecord<String, String> pollForRecord(Consumer<String, String> consumer, String orderId) {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(500));
            for (ConsumerRecord<String, String> r : records) {
                if (r.value() != null && r.value().contains(orderId)) {
                    return r;
                }
            }
        }
        return null;
    }
}
