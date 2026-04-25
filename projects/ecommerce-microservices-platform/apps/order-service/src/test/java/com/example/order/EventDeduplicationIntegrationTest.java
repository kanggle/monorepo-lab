package com.example.order;

import com.example.order.infrastructure.event.PaymentCompletedEventConsumer;
import com.example.order.infrastructure.event.PaymentRefundedEventConsumer;
import com.example.order.infrastructure.event.StockChangedEventConsumer;
import com.example.order.infrastructure.event.UserWithdrawnEventConsumer;
import com.example.messaging.outbox.ProcessedEventJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@DisplayName("이벤트 중복 처리 통합 테스트")
class EventDeduplicationIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("order_db")
            .withUsername("order_user")
            .withPassword("order_pass");

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PaymentCompletedEventConsumer paymentCompletedEventConsumer;

    @Autowired
    private PaymentRefundedEventConsumer paymentRefundedEventConsumer;

    @Autowired
    private StockChangedEventConsumer stockChangedEventConsumer;

    @Autowired
    private UserWithdrawnEventConsumer userWithdrawnEventConsumer;

    @Autowired
    private ProcessedEventJpaRepository processedEventJpaRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String PLACE_BODY = """
            {
              "items": [
                {"productId": "p1", "variantId": "v1", "productName": "노트북", "quantity": 1, "unitPrice": 500000}
              ],
              "shippingAddress": {
                "recipient": "홍길동", "phone": "010-1234-5678",
                "zipCode": "12345", "address1": "서울시 강남구"
              }
            }
            """;

    private String createOrder(String userId) throws Exception {
        String response = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_BODY))
                .andReturn().getResponse().getContentAsString();
        return response.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");
    }

    @Test
    @DisplayName("PaymentCompleted: 동일 event_id로 2회 수신 시 두 번째는 무시된다")
    void paymentCompleted_duplicateEventId_secondIsIgnored() throws Exception {
        String userId = "dedup-pay-" + System.nanoTime();
        String orderId = createOrder(userId);
        String eventId = UUID.randomUUID().toString();

        String eventJson = objectMapper.writeValueAsString(Map.of(
                "event_id", eventId,
                "event_type", "PaymentCompleted",
                "occurred_at", Instant.now().toString(),
                "source", "payment-service",
                "payload", Map.of(
                        "paymentId", "pay-1",
                        "orderId", orderId,
                        "userId", userId,
                        "amount", 500000,
                        "paidAt", Instant.now().toString()
                )
        ));

        paymentCompletedEventConsumer.onMessage(eventJson);
        assertThat(processedEventJpaRepository.existsByEventId(eventId)).isTrue();

        assertThatNoException().isThrownBy(() -> paymentCompletedEventConsumer.onMessage(eventJson));
    }

    @Test
    @DisplayName("PaymentRefunded: 동일 event_id로 2회 수신 시 두 번째는 무시된다")
    void paymentRefunded_duplicateEventId_secondIsIgnored() throws Exception {
        String eventId = UUID.randomUUID().toString();

        String eventJson = objectMapper.writeValueAsString(Map.of(
                "event_id", eventId,
                "event_type", "PaymentRefunded",
                "occurred_at", Instant.now().toString(),
                "source", "payment-service",
                "payload", Map.of(
                        "paymentId", "pay-ref-1",
                        "orderId", "nonexistent-order",
                        "userId", "user-1",
                        "amount", 500000,
                        "refundedAt", Instant.now().toString()
                )
        ));

        try {
            paymentRefundedEventConsumer.onMessage(eventJson);
        } catch (Exception ignored) {
        }

        assertThatNoException().isThrownBy(() -> paymentRefundedEventConsumer.onMessage(eventJson));
    }

    @Test
    @DisplayName("StockChanged: 동일 event_id로 2회 수신 시 두 번째는 무시된다")
    void stockChanged_duplicateEventId_secondIsIgnored() throws Exception {
        String userId = "dedup-stock-" + System.nanoTime();
        String orderId = createOrder(userId);
        String eventId = UUID.randomUUID().toString();

        String eventJson = objectMapper.writeValueAsString(Map.of(
                "event_id", eventId,
                "event_type", "StockChanged",
                "occurred_at", Instant.now().toString(),
                "source", "product-service",
                "payload", Map.of(
                        "productId", "p1",
                        "variantId", "v1",
                        "previousStock", 10,
                        "currentStock", 9,
                        "delta", -1,
                        "reason", "ORDER_RESERVED",
                        "orderId", orderId
                )
        ));

        stockChangedEventConsumer.onMessage(eventJson);
        assertThat(processedEventJpaRepository.existsByEventId(eventId)).isTrue();

        assertThatNoException().isThrownBy(() -> stockChangedEventConsumer.onMessage(eventJson));
    }

    @Test
    @DisplayName("UserWithdrawn: 동일 event_id로 2회 수신 시 두 번째는 무시된다")
    void userWithdrawn_duplicateEventId_secondIsIgnored() throws Exception {
        String userId = "dedup-user-" + System.nanoTime();
        createOrder(userId);
        String eventId = UUID.randomUUID().toString();

        String eventJson = objectMapper.writeValueAsString(Map.of(
                "event_id", eventId,
                "event_type", "UserWithdrawn",
                "occurred_at", Instant.now().toString(),
                "source", "user-service",
                "payload", Map.of(
                        "userId", userId,
                        "withdrawnAt", Instant.now().toString()
                )
        ));

        userWithdrawnEventConsumer.onMessage(eventJson);
        assertThat(processedEventJpaRepository.existsByEventId(eventId)).isTrue();

        assertThatNoException().isThrownBy(() -> userWithdrawnEventConsumer.onMessage(eventJson));
    }

    @Test
    @DisplayName("event_id가 null인 이벤트는 중복 체크 없이 정상 처리된다")
    void nullEventId_processedWithoutDeduplication() throws Exception {
        String userId = "dedup-null-" + System.nanoTime();
        String orderId = createOrder(userId);

        String eventJson = objectMapper.writeValueAsString(Map.of(
                "event_type", "PaymentCompleted",
                "occurred_at", Instant.now().toString(),
                "source", "payment-service",
                "payload", Map.of(
                        "paymentId", "pay-null-1",
                        "orderId", orderId,
                        "userId", userId,
                        "amount", 500000,
                        "paidAt", Instant.now().toString()
                )
        ));

        assertThatNoException().isThrownBy(() -> paymentCompletedEventConsumer.onMessage(eventJson));
    }
}
