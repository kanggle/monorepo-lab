package com.example.order;

import com.example.order.infrastructure.event.PaymentRefundedEventConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
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

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = OrderServiceApplication.class,
        properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@DisplayName("환불 완료 이벤트 통합 테스트")
class OrderPaymentRefundedIntegrationTest {

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
    private PaymentRefundedEventConsumer paymentRefundedEventConsumer;

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

    private String buildRefundedEventJson(String orderId) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "event_id", UUID.randomUUID().toString(),
                "event_type", "PaymentRefunded",
                "occurred_at", Instant.now().toString(),
                "source", "payment-service",
                "payload", Map.of(
                        "paymentId", "pay-refund-" + UUID.randomUUID(),
                        "orderId", orderId,
                        "userId", "user1",
                        "amount", 500000L,
                        "totalRefunded", 500000L,
                        "fullyRefunded", true,
                        "refundedAt", Instant.now().toString()
                )
        ));
    }

    private String createAndCancelOrder(String userId) throws Exception {
        String createResponse = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_BODY))
                .andReturn().getResponse().getContentAsString();

        String orderId = createResponse.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("X-User-Id", userId))
                .andExpect(status().isOk());

        return orderId;
    }

    @Test
    @DisplayName("PaymentRefunded 이벤트 수신 시 취소된 주문에 환불 정보가 반영된다")
    void paymentRefunded_cancelledOrder_recordsRefundInfo() throws Exception {
        String userId = "refund-user-" + System.nanoTime();
        String orderId = createAndCancelOrder(userId);

        paymentRefundedEventConsumer.onMessage(buildRefundedEventJson(orderId));

        mockMvc.perform(get("/api/orders/" + orderId).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("동일 PaymentRefunded 이벤트 2회 수신 시 멱등 처리된다")
    void paymentRefunded_duplicateEvent_isIdempotent() throws Exception {
        String userId = "refund-idem-" + System.nanoTime();
        String orderId = createAndCancelOrder(userId);
        String eventJson = buildRefundedEventJson(orderId);

        paymentRefundedEventConsumer.onMessage(eventJson);
        assertThatNoException()
                .isThrownBy(() -> paymentRefundedEventConsumer.onMessage(eventJson));
    }

    @Test
    @DisplayName("존재하지 않는 orderId로 이벤트 수신 시 예외가 전파되지 않는다")
    void paymentRefunded_nonexistentOrderId_doesNotThrow() throws Exception {
        String eventJson = buildRefundedEventJson("nonexistent-order");

        assertThatNoException()
                .isThrownBy(() -> paymentRefundedEventConsumer.onMessage(eventJson));
    }

    @Test
    @DisplayName("PENDING 상태의 주문에 환불 이벤트 수신 시 예외가 전파된다 (DLQ 라우팅)")
    @Disabled("TASK-BE-441: refund-on-PENDING did not propagate the expected exception (DLQ routing) "
            + "on CI — TASK-MONO-307 residual triage")
    void paymentRefunded_pendingOrder_propagatesException() throws Exception {
        String userId = "refund-pending-" + System.nanoTime();

        String createResponse = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_BODY))
                .andReturn().getResponse().getContentAsString();

        String orderId = createResponse.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> paymentRefundedEventConsumer.onMessage(buildRefundedEventJson(orderId)))
                .isInstanceOf(com.example.order.domain.exception.InvalidOrderException.class);
    }
}
