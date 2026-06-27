package com.example.order;

import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.repository.OrderRepository;
import com.example.order.infrastructure.event.OrderOutboxPublisher;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
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

/**
 * Outbox v2 publish round-trip IT (TASK-BE-448). Rewritten from the v1 version: the
 * write path now persists to {@code order_outbox} (UUID PK, {@code published_at}-based,
 * no {@code status} column) and the {@link OrderOutboxPublisher} relay drains it.
 *
 * <p>The {@code @Scheduled} poller's initial delay is set large so it stays dormant
 * during the create/rollback assertions (pending rows are observed deterministically);
 * the drain test drives {@link OrderOutboxPublisher#publishPending()} explicitly.
 *
 * <p>Runs on the CI Linux Testcontainers lane ({@code :integrationTest}), the
 * authoritative verifier for this service.
 */
@SpringBootTest(classes = OrderServiceApplication.class, properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "order.outbox.initial-delay-ms=600000",
        "order.outbox.poll-ms=1000"
})
@Tag("integration")
@Testcontainers
@AutoConfigureMockMvc
@EmbeddedKafka(partitions = 1)
@DisplayName("Outbox v2 이벤트 발행 통합 테스트")
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

    @Autowired
    private OrderOutboxPublisher outboxPublisher;

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
    @DisplayName("주문 생성 트랜잭션 커밋 후 order_outbox 에 OrderPlaced v2 행이 저장된다 (pending)")
    void placeOrder_afterCommit_outboxEntryCreated() throws Exception {
        String userId = "event-test-user-" + System.nanoTime();

        String response = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String orderId = response.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM order_outbox WHERE aggregate_id = ? AND event_type = 'OrderPlaced'", orderId);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("aggregate_type")).isEqualTo("Order");
        assertThat(rows.get(0).get("event_id")).isNotNull();
        assertThat(rows.get(0).get("published_at")).isNull(); // poller dormant → still pending
    }

    @Test
    @DisplayName("주문 취소 트랜잭션 커밋 후 order_outbox 에 OrderCancelled v2 행이 저장된다")
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

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM order_outbox WHERE aggregate_id = ? AND event_type = 'OrderCancelled'", orderId);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("aggregate_type")).isEqualTo("Order");
    }

    @Test
    @DisplayName("트랜잭션 롤백 시 order_outbox 레코드도 롤백되어 저장되지 않는다")
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
                "SELECT COUNT(*) FROM order_outbox", Long.class);

        mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", "rollback-user")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidBody))
                .andExpect(status().is4xxClientError());

        long countAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM order_outbox", Long.class);

        assertThat(countAfter).isEqualTo(countBefore);
    }

    @Test
    @DisplayName("DB 저장과 order_outbox 저장이 원자적으로 동작한다 — 커밋 후 일관된다")
    @Disabled("TASK-BE-439: order read/mapping LazyInitializationException (OrderJpaEntity.items, "
            + "detached entity, no Session) — TASK-MONO-307 quarantine")
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

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM order_outbox WHERE aggregate_id = ?", orderId);
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("event_type")).isEqualTo("OrderPlaced");
    }

    @Test
    @DisplayName("relay 가 미발행 order_outbox 행을 발행하고 published_at 을 채운다 (Kafka ACK)")
    void relay_publishesPendingRows_setsPublishedAt() throws Exception {
        String userId = "polling-user-" + System.nanoTime();

        String response = mockMvc.perform(post("/api/orders")
                        .header("X-User-Id", userId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(PLACE_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        String orderId = response.replaceAll(".*\"orderId\":\"([^\"]+)\".*", "$1");

        // Drive the relay explicitly (the @Scheduled poller is dormant in this test).
        outboxPublisher.publishPending();

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT * FROM order_outbox WHERE aggregate_id = ? AND event_type = 'OrderPlaced'", orderId);

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).get("published_at")).isNotNull();
    }
}
