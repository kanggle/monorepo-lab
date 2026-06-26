package com.example.order;

import com.example.order.infrastructure.event.StockChangedEventConsumer;
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

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = OrderServiceApplication.class,
        properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@DisplayName("주문 확정 통합 테스트")
class OrderConfirmationIntegrationTest {

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
    private StockChangedEventConsumer stockChangedEventConsumer;

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

    private String buildStockChangedJson(String reason, String orderId) throws Exception {
        var payloadMap = new java.util.HashMap<String, Object>();
        payloadMap.put("productId", "p1");
        payloadMap.put("variantId", "v1");
        payloadMap.put("previousStock", 10);
        payloadMap.put("currentStock", 9);
        payloadMap.put("delta", -1);
        payloadMap.put("reason", reason);
        payloadMap.put("orderId", orderId);

        return objectMapper.writeValueAsString(Map.of(
                "eventId", UUID.randomUUID().toString(),
                "eventType", "StockChanged",
                "occurredAt", "2026-03-23T00:00:00Z",
                "source", "product-service",
                "payload", payloadMap
        ));
    }

    @Test
    @DisplayName("StockChanged ORDER_RESERVED 이벤트 수신 시 주문이 CONFIRMED 상태로 변경된다")
    void stockChanged_orderReserved_confirmsOrder() throws Exception {
        String userId = "confirm-user-" + System.nanoTime();

        String createResponse = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_BODY))
                .andReturn().getResponse().getContentAsString();

        String orderId = createResponse.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");

        stockChangedEventConsumer.onMessage(buildStockChangedJson("ORDER_RESERVED", orderId));

        mockMvc.perform(get("/api/orders/" + orderId).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    @DisplayName("RESTOCK 이벤트 수신 시 주문 상태는 변경되지 않는다")
    void stockChanged_restock_doesNotChangeOrderStatus() throws Exception {
        String userId = "restock-user-" + System.nanoTime();

        String createResponse = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_BODY))
                .andReturn().getResponse().getContentAsString();

        String orderId = createResponse.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");

        stockChangedEventConsumer.onMessage(buildStockChangedJson("RESTOCK", null));

        mockMvc.perform(get("/api/orders/" + orderId).header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @DisplayName("존재하지 않는 orderId로 ORDER_RESERVED 이벤트 수신 시 예외가 전파된다 (DLQ 라우팅)")
    void stockChanged_nonexistentOrderId_propagatesException() throws Exception {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                stockChangedEventConsumer.onMessage(
                        buildStockChangedJson("ORDER_RESERVED", "nonexistent-id"))
        ).isInstanceOf(com.example.order.domain.exception.OrderNotFoundException.class);
    }
}
