package com.example.order;

import com.example.order.infrastructure.event.PaymentCompletedEventConsumer;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@DisplayName("결제 완료 이벤트 통합 테스트")
class OrderPaymentCompletedIntegrationTest {

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

    private String buildEventJson(String paymentId, String orderId, String userId, long amount) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
                "event_id", UUID.randomUUID().toString(),
                "event_type", "PaymentCompleted",
                "occurred_at", Instant.now().toString(),
                "source", "payment-service",
                "payload", Map.of(
                        "paymentId", paymentId,
                        "orderId", orderId,
                        "userId", userId,
                        "amount", amount,
                        "paidAt", Instant.now().toString()
                )
        ));
    }

    @Test
    @DisplayName("PaymentCompleted 이벤트 수신 시 주문에 결제 정보가 반영된다")
    void paymentCompleted_validEvent_recordsPaymentInfo() throws Exception {
        String userId = "pay-user-" + System.nanoTime();

        String createResponse = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_BODY))
                .andReturn().getResponse().getContentAsString();

        String orderId = createResponse.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");

        paymentCompletedEventConsumer.onMessage(buildEventJson("pay-123", orderId, userId, 500000L));

        mockMvc.perform(get("/api/orders/" + orderId).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("동일 PaymentCompleted 이벤트 2회 수신 시 멱등 처리된다")
    void paymentCompleted_duplicateEvent_isIdempotent() throws Exception {
        String userId = "pay-idem-" + System.nanoTime();

        String createResponse = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_BODY))
                .andReturn().getResponse().getContentAsString();

        String orderId = createResponse.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");
        String eventJson = buildEventJson("pay-dup", orderId, userId, 500000L);

        paymentCompletedEventConsumer.onMessage(eventJson);
        // 두 번째 호출도 예외 없이 처리
        org.assertj.core.api.Assertions.assertThatNoException()
                .isThrownBy(() -> paymentCompletedEventConsumer.onMessage(eventJson));
    }

    @Test
    @DisplayName("존재하지 않는 orderId로 이벤트 수신 시 예외가 전파되지 않는다")
    void paymentCompleted_nonexistentOrderId_doesNotThrow() throws Exception {
        String eventJson = buildEventJson("pay-xxx", "nonexistent-order", "user1", 50000L);

        org.assertj.core.api.Assertions.assertThatNoException()
                .isThrownBy(() -> paymentCompletedEventConsumer.onMessage(eventJson));
    }
}
