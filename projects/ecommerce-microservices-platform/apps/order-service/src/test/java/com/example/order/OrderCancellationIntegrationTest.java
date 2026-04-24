package com.example.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Testcontainers
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@DisplayName("주문 취소 통합 테스트")
class OrderCancellationIntegrationTest {

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

    @Test
    @DisplayName("주문 생성 후 취소 시 CANCELLED 상태로 변경된다")
    void cancelOrder_afterPlacing_returnsCancelled() throws Exception {
        String userId = "cancel-user-" + System.nanoTime();

        String createResponse = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_BODY))
                .andReturn().getResponse().getContentAsString();

        String orderId = createResponse.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("다른 사용자가 취소 시 403 반환")
    void cancelOrder_differentUser_returns403() throws Exception {
        String userId = "owner-cancel-" + System.nanoTime();

        String createResponse = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_BODY))
                .andReturn().getResponse().getContentAsString();

        String orderId = createResponse.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("X-User-Id", "attacker"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("존재하지 않는 주문 취소 시 404 반환")
    void cancelOrder_notFound_returns404() throws Exception {
        mockMvc.perform(post("/api/orders/nonexistent-id/cancel")
                        .header("X-User-Id", "user1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    @Test
    @DisplayName("이미 취소된 주문 재취소 시 422 반환")
    void cancelOrder_alreadyCancelled_returns422() throws Exception {
        String userId = "recancel-user-" + System.nanoTime();

        String createResponse = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_BODY))
                .andReturn().getResponse().getContentAsString();

        String orderId = createResponse.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                .header("X-User-Id", userId));

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("X-User-Id", userId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("ORDER_CANNOT_BE_CANCELLED"));
    }
}
