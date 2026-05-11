package com.example.order;

import com.example.order.application.saga.OrderStuckDetector;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testcontainers integration test for the order saga stuck-detector
 * (TASK-BE-138, ADR-MONO-005 § D3 Category A).
 *
 * <p>Drives the real {@link OrderStuckDetector} +
 * {@link com.example.order.application.saga.OrderStuckRecoveryHandler} +
 * {@link OrderRepository} (Postgres) + outbox writer end-to-end. Asserts:
 *
 * <ol>
 *   <li>Single sweep on a stuck PENDING order increments the attempt counter
 *       without transitioning to terminal.</li>
 *   <li>5 sweeps (cap reached) transition the order to {@code STUCK_RECOVERY_FAILED}
 *       and write a fresh {@code OrderSagaRecoveryExhausted} outbox row.</li>
 *   <li>Orders with {@code payment_id IS NOT NULL} are ignored even when the
 *       row is past the grace cut-off.</li>
 *   <li>Orders within the grace cut-off (created recently) are ignored.</li>
 * </ol>
 *
 * <p>The {@code @Scheduled} initial-delay is set to a day so the detector
 * never auto-fires during the test; the IT drives {@code sweep()} directly.
 */
@SpringBootTest(classes = com.example.order.OrderServiceApplication.class,
        properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "outbox.polling.interval-ms=600000",
        "order.saga.stuck-detector.initial-delay-ms=86400000",
        "order.saga.stuck-detector.fixed-delay-ms=86400000",
        "order.saga.stuck-detector.threshold-seconds=1800",
        "order.saga.stuck-detector.max-attempts=5"
})
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1)
@DisplayName("Order stuck-detector 통합 테스트")
class OrderStuckRecoveryIT {

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

    @Autowired private OrderStuckDetector detector;
    @Autowired private OrderRepository orderRepository;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    void cleanState() {
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
    }

    @AfterEach
    void tearDown() {
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM order_items");
        jdbc.update("DELETE FROM orders");
    }

    @Test
    @DisplayName("PENDING + payment_id=null + grace 경과 row 1회 sweep — attempt count = 1, status PENDING 유지, 알림 outbox 미발행")
    void singleSweep_bumpsAttemptCount_noExhaustion() {
        String orderId = seedStuckOrder();

        detector.sweep();

        Order reloaded = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloaded.getStuckRecoveryAttemptCount()).isEqualTo(1);
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(countAlertOutboxRows(orderId)).isZero();
    }

    @Test
    @DisplayName("5회 sweep — 마지막 호출에 STUCK_RECOVERY_FAILED 전이 + alert outbox 1행 발행")
    void fiveSweeps_marksExhaustedAndWritesAlertOutbox() {
        String orderId = seedStuckOrder();

        for (int i = 0; i < detector.maxAttempts(); i++) {
            detector.sweep();
        }

        Order reloaded = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.STUCK_RECOVERY_FAILED);
        assertThat(countAlertOutboxRows(orderId)).isEqualTo(1);
    }

    @Test
    @DisplayName("PENDING 이지만 payment_id != null 인 row 는 sweep 후 변경 없음")
    void orderWithPayment_isIgnored() {
        String orderId = seedOrder(/*paymentId*/ "pay-1", /*createdAtSecondsAgo*/ 7200);

        detector.sweep();

        Order reloaded = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloaded.getStuckRecoveryAttemptCount()).isZero();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("PENDING + payment_id=null 이지만 grace 미경과 row 는 sweep 후 변경 없음")
    void orderWithinGracePeriod_isIgnored() {
        String orderId = seedOrder(/*paymentId*/ null, /*createdAtSecondsAgo*/ 60);

        detector.sweep();

        Order reloaded = orderRepository.findById(orderId).orElseThrow();
        assertThat(reloaded.getStuckRecoveryAttemptCount()).isZero();
        assertThat(reloaded.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    private String seedStuckOrder() {
        return seedOrder(/*paymentId*/ null, /*createdAtSecondsAgo*/ 7200);
    }

    /**
     * Seeds a single PENDING order via JdbcTemplate so we can pre-date
     * {@code created_at} past the grace threshold.
     */
    private String seedOrder(String paymentId, long createdAtSecondsAgo) {
        String orderId = UUID.randomUUID().toString();
        Instant createdAt = Instant.now().minusSeconds(createdAtSecondsAgo);
        // The Order aggregate refuses construction without items, so we
        // bypass the JPA mapper for IT seeding and insert directly.
        jdbc.update("INSERT INTO orders (order_id, user_id, status, total_price, " +
                        "recipient, phone, zip_code, address1, address2, " +
                        "created_at, updated_at, payment_id, paid_at, refunded_at, " +
                        "stuck_recovery_attempt_count, stuck_recovery_at, version) " +
                        "VALUES (?, ?, 'PENDING', 0, '홍길동', '010-0000-0000', '12345', " +
                        "'서울시 강남구', NULL, ?, ?, ?, NULL, NULL, 0, NULL, 0)",
                orderId, "user-" + UUID.randomUUID(),
                java.sql.Timestamp.from(createdAt),
                java.sql.Timestamp.from(createdAt),
                paymentId);
        // OrderJpaEntity has @OneToMany items; an empty collection is fine.
        return orderId;
    }

    private int countAlertOutboxRows(String orderId) {
        List<Map<String, Object>> rows = jdbc.queryForList(
                "SELECT 1 FROM outbox WHERE event_type = ? AND aggregate_id = ?",
                "OrderSagaRecoveryExhausted", orderId);
        return rows.size();
    }
}
