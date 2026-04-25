package com.example.order;

import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.repository.OrderRepository;
import com.example.messaging.outbox.OutboxPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "outbox.polling.interval-ms=500"
})
@Tag("integration")
@Testcontainers
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@DisplayName("Outbox 이벤트 발행 통합 테스트")
class OrderEventPublishIntegrationTest {

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
    @DisplayName("주문 생성 트랜잭션 커밋 후 outbox 테이블에 OrderPlaced 이벤트가 저장된다")
    void placeOrder_afterCommit_outboxEntryCreated() throws Exception {
        String userId = "event-test-user-" + System.nanoTime();

        String response = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String orderId = response.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");

        List<Map<String, Object>> outboxEntries = jdbcTemplate.queryForList(
                "SELECT * FROM outbox WHERE aggregate_id = ? AND event_type = 'OrderPlaced'", orderId);

        assertThat(outboxEntries).hasSize(1);
        assertThat(outboxEntries.get(0).get("aggregate_type")).isEqualTo("Order");
        assertThat(outboxEntries.get(0).get("status")).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("주문 취소 트랜잭션 커밋 후 outbox 테이블에 OrderCancelled 이벤트가 저장된다")
    void cancelOrder_afterCommit_outboxEntryCreated() throws Exception {
        String userId = "event-cancel-user-" + System.nanoTime();

        String createResponse = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_BODY))
                .andReturn().getResponse().getContentAsString();

        String orderId = createResponse.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");

        mockMvc.perform(post("/api/orders/" + orderId + "/cancel")
                        .header("X-User-Id", userId))
                .andExpect(status().isOk());

        List<Map<String, Object>> outboxEntries = jdbcTemplate.queryForList(
                "SELECT * FROM outbox WHERE aggregate_id = ? AND event_type = 'OrderCancelled'", orderId);

        assertThat(outboxEntries).hasSize(1);
        assertThat(outboxEntries.get(0).get("aggregate_type")).isEqualTo("Order");
    }

    @Test
    @DisplayName("트랜잭션 롤백 시 outbox 레코드도 롤백되어 저장되지 않는다")
    void placeOrder_rollback_noOutboxEntry() throws Exception {
        String invalidBody = """
                {
                  "items": [],
                  "shippingAddress": {
                    "recipient": "홍길동", "phone": "010-1234-5678",
                    "zipCode": "12345", "address1": "서울시 강남구"
                  }
                }
                """;

        long countBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox", Long.class);

        mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", "rollback-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().is4xxClientError());

        long countAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox", Long.class);

        assertThat(countAfter).isEqualTo(countBefore);
    }

    @Test
    @DisplayName("DB 저장과 outbox 저장이 원자적으로 동작한다 — 커밋 후 DB 데이터와 outbox가 일관된다")
    void placeOrder_commitSuccess_dbAndOutboxConsistent() throws Exception {
        String userId = "atomic-user-" + System.nanoTime();

        String createResponse = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String orderId = createResponse.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");

        Optional<Order> saved = orderRepository.findById(orderId);
        assertThat(saved).isPresent();
        assertThat(saved.get().getStatus()).isEqualTo(OrderStatus.PENDING);

        List<Map<String, Object>> outboxEntries = jdbcTemplate.queryForList(
                "SELECT * FROM outbox WHERE aggregate_id = ?", orderId);
        assertThat(outboxEntries).hasSize(1);
        assertThat(outboxEntries.get(0).get("event_type")).isEqualTo("OrderPlaced");
    }

    @Test
    @DisplayName("폴링 스케줄러가 미발행 outbox 이벤트를 발행 완료로 처리한다")
    void pollingScheduler_publishesPendingEvents() throws Exception {
        String userId = "polling-user-" + System.nanoTime();

        String response = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String orderId = response.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");

        Thread.sleep(3000);

        List<Map<String, Object>> outboxEntries = jdbcTemplate.queryForList(
                "SELECT * FROM outbox WHERE aggregate_id = ? AND event_type = 'OrderPlaced'", orderId);

        assertThat(outboxEntries).hasSize(1);
        assertThat(outboxEntries.get(0).get("status")).isEqualTo("PUBLISHED");
        assertThat(outboxEntries.get(0).get("published_at")).isNotNull();
    }
}
