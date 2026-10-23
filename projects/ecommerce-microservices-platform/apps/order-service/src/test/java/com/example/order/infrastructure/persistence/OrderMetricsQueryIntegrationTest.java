package com.example.order.infrastructure.persistence;

import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderItem;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.model.ShippingAddress;
import com.example.order.domain.repository.OrderRepository;
import com.example.order.domain.repository.PaidOrderMetricRow;
import com.example.order.domain.repository.ProductOrderRankingRow;
import com.example.order.domain.repository.SellerOrderRankingRow;
import com.example.order.domain.seller.SellerScopeContext;
import com.example.order.domain.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which orders the overview performance metrics and the insights rankings count
 * (ADR-MONO-084 D5, TASK-MONO-807 AC-1 · AC-2 · AC-3) — against real PostgreSQL, because
 * the rule lives in the JPQL, not in Java.
 *
 * <p>Control groups first: every status that must NOT count is seeded next to the ones that
 * must, so a predicate that widened (or vanished) moves the totals. Each test uses its own
 * fresh tenant, so rows from other tests cannot leak in.
 */
@SpringBootTest(classes = com.example.order.OrderServiceApplication.class,
        properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1)
@DisplayName("주문 실적 지표 · 순위 집계 쿼리 통합 테스트 (TASK-MONO-807)")
class OrderMetricsQueryIntegrationTest {

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

    /** Window under test: [FROM, TO). */
    private static final Instant FROM = Instant.parse("2026-10-18T15:00:00Z");
    private static final Instant TO = Instant.parse("2026-10-22T16:30:00Z");
    private static final Instant INSIDE = Instant.parse("2026-10-20T03:00:00Z");

    @Autowired
    private OrderRepository orderRepository;

    private String tenant;

    @BeforeEach
    void freshTenant() {
        tenant = "metrics-" + UUID.randomUUID();
        TenantContext.set(tenant);
        SellerScopeContext.set(null);
    }

    @AfterEach
    void clear() {
        TenantContext.clear();
        SellerScopeContext.set(null);
    }

    @Test
    @DisplayName("CONFIRMED · SHIPPED · DELIVERED 만 센다 — PENDING · BACKORDERED · CANCELLED · 환불 · STUCK 은 대조군")
    void only_realised_sales_count() {
        // Each status gets a distinct price so a leaked row is identifiable by its amount.
        seed(OrderStatus.PENDING, 1_000, INSIDE, null, line("p-pending", "s1", 1, 1_000));
        seed(OrderStatus.BACKORDERED, 2_000, INSIDE, null, line("p-backordered", "s1", 1, 2_000));
        seed(OrderStatus.CANCELLED, 4_000, INSIDE, null, line("p-cancelled", "s1", 1, 4_000));
        seed(OrderStatus.CANCELLED, 8_000, INSIDE, INSIDE, line("p-refunded", "s1", 1, 8_000));
        seed(OrderStatus.STUCK_RECOVERY_FAILED, 16_000, INSIDE, null, line("p-stuck", "s1", 1, 16_000));
        seed(OrderStatus.CONFIRMED, 100_000, INSIDE, null, line("p-confirmed", "s1", 1, 100_000));
        seed(OrderStatus.SHIPPED, 200_000, INSIDE, null, line("p-shipped", "s1", 1, 200_000));
        seed(OrderStatus.DELIVERED, 400_000, INSIDE, null, line("p-delivered", "s1", 1, 400_000));

        List<PaidOrderMetricRow> rows = orderRepository.findPaidOrderMetrics(FROM, TO);
        assertThat(rows).extracting(PaidOrderMetricRow::revenue)
                .containsExactlyInAnyOrder(100_000L, 200_000L, 400_000L);

        assertThat(orderRepository.aggregateProductRanking(FROM, TO))
                .extracting(ProductOrderRankingRow::productId)
                .containsExactlyInAnyOrder("p-confirmed", "p-shipped", "p-delivered");
        assertThat(orderRepository.aggregateSellerRanking(FROM, TO))
                .singleElement()
                .satisfies(s -> {
                    assertThat(s.orderCount()).isEqualTo(3);
                    assertThat(s.revenue()).isEqualTo(700_000);
                });
    }

    @Test
    @DisplayName("결제 시각으로 귀속한다 — 생성 시각이 아니다, 구간은 [from, to)")
    void attributed_to_paid_at_half_open() {
        // placed last week, paid inside → counts
        seedAt(OrderStatus.CONFIRMED, 10_000, Instant.parse("2026-10-10T00:00:00Z"), INSIDE, null,
                line("p-paid-inside", "s1", 1, 10_000));
        // placed inside, paid before the window → does not
        seedAt(OrderStatus.CONFIRMED, 20_000, INSIDE, Instant.parse("2026-10-18T14:59:59Z"), null,
                line("p-paid-before", "s1", 1, 20_000));
        // paid exactly at `from` → counts; exactly at `to` → does not
        seed(OrderStatus.DELIVERED, 30_000, FROM, null, line("p-at-from", "s1", 1, 30_000));
        seed(OrderStatus.DELIVERED, 40_000, TO, null, line("p-at-to", "s1", 1, 40_000));
        // confirmed but its paid_at has not landed yet → not yet
        seed(OrderStatus.CONFIRMED, 50_000, null, null, line("p-unpaid-confirmed", "s1", 1, 50_000));

        assertThat(orderRepository.findPaidOrderMetrics(FROM, TO))
                .extracting(PaidOrderMetricRow::revenue)
                .containsExactlyInAnyOrder(10_000L, 30_000L);
        assertThat(orderRepository.aggregateProductRanking(FROM, TO))
                .extracting(ProductOrderRankingRow::productId)
                .containsExactlyInAnyOrder("p-paid-inside", "p-at-from");
    }

    @Test
    @DisplayName("매출은 쿠폰이 빠진 결제 금액(total_price), 수량은 줄의 합")
    void revenue_is_what_the_customer_paid() {
        // lines 30,000 + 2 × 10,000 = 50,000; a 5,000 coupon → total_price 45,000
        seed(OrderStatus.CONFIRMED, 45_000, INSIDE, null,
                line("p-a", "s1", 1, 30_000), line("p-b", "s2", 2, 10_000));

        assertThat(orderRepository.findPaidOrderMetrics(FROM, TO)).singleElement().satisfies(r -> {
            assertThat(r.revenue()).isEqualTo(45_000);
            assertThat(r.units()).isEqualTo(3);
            assertThat(r.paidAt()).isEqualTo(INSIDE);
        });
    }

    @Test
    @DisplayName("셀러 범위가 걸리면 그 셀러의 줄만 — 매출 · 수량 · 순위 모두")
    void seller_scope_narrows_to_own_lines() {
        seed(OrderStatus.CONFIRMED, 45_000, INSIDE, null,
                line("p-a", "s1", 1, 30_000), line("p-b", "s2", 2, 10_000));
        seed(OrderStatus.SHIPPED, 7_000, INSIDE, null, line("p-c", "s1", 1, 7_000));

        SellerScopeContext.set("s2");

        assertThat(orderRepository.findPaidOrderMetrics(FROM, TO)).singleElement().satisfies(r -> {
            assertThat(r.revenue()).as("s2's lines, before the order-level coupon").isEqualTo(20_000);
            assertThat(r.units()).isEqualTo(2);
        });
        assertThat(orderRepository.aggregateSellerRanking(FROM, TO))
                .extracting(SellerOrderRankingRow::sellerId).containsExactly("s2");
        assertThat(orderRepository.aggregateProductRanking(FROM, TO))
                .extracting(ProductOrderRankingRow::productId).containsExactly("p-b");

        // control: unrestricted sees both orders, at what the customers paid
        SellerScopeContext.set("*");
        assertThat(orderRepository.findPaidOrderMetrics(FROM, TO))
                .extracting(PaidOrderMetricRow::revenue).containsExactlyInAnyOrder(45_000L, 7_000L);
    }

    @Test
    @DisplayName("다른 테넌트의 주문은 잡히지 않는다")
    void tenant_isolation() {
        seed(OrderStatus.CONFIRMED, 10_000, INSIDE, null, line("p-mine", "s1", 1, 10_000));
        String mine = tenant;

        TenantContext.set("metrics-other-" + UUID.randomUUID());
        seed(OrderStatus.CONFIRMED, 99_000, INSIDE, null, line("p-theirs", "s1", 1, 99_000));

        TenantContext.set(mine);
        assertThat(orderRepository.findPaidOrderMetrics(FROM, TO))
                .extracting(PaidOrderMetricRow::revenue).containsExactly(10_000L);
        assertThat(orderRepository.aggregateProductRanking(FROM, TO))
                .extracting(ProductOrderRankingRow::productId).containsExactly("p-mine");
    }

    // ---- seeding --------------------------------------------------------------------------

    private OrderItem line(String productId, String sellerId, int quantity, long unitPrice) {
        // order_items.variant_id is NOT NULL (V1)
        return OrderItem.reconstitute(UUID.randomUUID().toString(), productId, "v-" + productId,
                "상품 " + productId, null, quantity, unitPrice, sellerId);
    }

    private void seed(OrderStatus status, long totalPrice, Instant paidAt, Instant refundedAt, OrderItem... items) {
        seedAt(status, totalPrice, Instant.parse("2026-10-01T00:00:00Z"), paidAt, refundedAt, items);
    }

    private void seedAt(OrderStatus status, long totalPrice, Instant createdAt, Instant paidAt,
                        Instant refundedAt, OrderItem... items) {
        Order order = Order.reconstitute(
                UUID.randomUUID().toString(), "user-" + UUID.randomUUID(), List.of(items),
                status, totalPrice,
                ShippingAddress.reconstitute("홍길동", "010-1234-5678", "12345", "서울시 강남구", null),
                createdAt, createdAt,
                // payment_id is VARCHAR(36) (V2) — a bare UUID, no prefix
                paidAt == null ? null : UUID.randomUUID().toString(), paidAt, refundedAt,
                0, null, null);
        orderRepository.save(order);
    }
}
