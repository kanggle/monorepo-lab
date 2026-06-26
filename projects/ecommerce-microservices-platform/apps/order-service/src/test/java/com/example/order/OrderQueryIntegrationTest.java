package com.example.order;

import com.example.order.domain.model.Order;
import com.example.order.domain.repository.OrderRepository;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = OrderServiceApplication.class,
        properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@DisplayName("주문 조회 통합 테스트")
class OrderQueryIntegrationTest {

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
    private OrderRepository orderRepository;

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
    @DisplayName("주문 생성 후 목록 조회 시 해당 주문이 반환된다")
    void getOrders_afterPlacing_returnsOrderInList() throws Exception {
        String userId = "query-user-" + System.nanoTime();

        mockMvc.perform(post("/api/orders")
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(PLACE_BODY));

        mockMvc.perform(get("/api/orders")
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("주문 생성 후 상세 조회 시 items와 shippingAddress가 반환된다")
    void getOrder_afterPlacing_returnsDetail() throws Exception {
        String userId = "detail-user-" + System.nanoTime();

        String createResponse = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_BODY))
                .andReturn().getResponse().getContentAsString();

        String orderId = createResponse.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("X-User-Id", userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(orderId))
                .andExpect(jsonPath("$.items").isArray())
                .andExpect(jsonPath("$.shippingAddress.recipient").value("홍길동"));
    }

    @Test
    @DisplayName("다른 사용자의 주문 상세 조회 시 403 반환")
    void getOrder_differentUser_returns403() throws Exception {
        String userId = "owner-" + System.nanoTime();

        String createResponse = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_BODY))
                .andReturn().getResponse().getContentAsString();

        String orderId = createResponse.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(get("/api/orders/" + orderId)
                        .header("X-User-Id", "other-user"))
                .andExpect(status().isForbidden())
                // Contract order-api.md §GET /api/orders/{orderId}: 403 | ACCESS_DENIED | Not the order owner.
                // UNAUTHORIZED is the 401 code (missing header); ACCESS_DENIED is the correct 403 code.
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("존재하지 않는 orderId 조회 시 404 반환")
    void getOrder_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/orders/nonexistent-id")
                        .header("X-User-Id", "user1"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ORDER_NOT_FOUND"));
    }

    // ─── status 필터 통합 테스트 ──────────────────────────────────────

    @Test
    @DisplayName("status=PENDING 필터 시 PENDING 주문만 반환된다")
    void getOrders_withStatusPending_returnsFilteredOrders() throws Exception {
        String userId = "status-filter-user-" + System.nanoTime();

        mockMvc.perform(post("/api/orders")
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(PLACE_BODY));

        mockMvc.perform(get("/api/orders")
                        .header("X-User-Id", userId)
                        .param("status", "PENDING"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("PENDING"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("존재하지 않는 status로 필터 시 빈 결과를 반환한다")
    void getOrders_withStatusNoMatch_returnsEmpty() throws Exception {
        String userId = "status-empty-user-" + System.nanoTime();

        mockMvc.perform(post("/api/orders")
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(PLACE_BODY));

        mockMvc.perform(get("/api/orders")
                        .header("X-User-Id", userId)
                        .param("status", "SHIPPED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    @DisplayName("유효하지 않은 status 값이면 400 반환")
    void getOrders_invalidStatus_returns400() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .header("X-User-Id", "user1")
                        .param("status", "INVALID"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_ORDER_REQUEST"));
    }

    @Test
    @DisplayName("빈 status 파라미터이면 전체 주문을 반환한다")
    void getOrders_emptyStatus_returnsAll() throws Exception {
        String userId = "empty-status-user-" + System.nanoTime();

        mockMvc.perform(post("/api/orders")
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(PLACE_BODY));

        mockMvc.perform(get("/api/orders")
                        .header("X-User-Id", userId)
                        .param("status", ""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
