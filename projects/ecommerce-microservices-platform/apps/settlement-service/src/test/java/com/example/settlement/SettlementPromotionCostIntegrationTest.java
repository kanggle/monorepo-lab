package com.example.settlement;

import com.example.settlement.domain.model.PromotionCost;
import com.example.settlement.domain.model.PromotionCostType;
import com.example.settlement.domain.model.SellerBalance;
import com.example.settlement.domain.repository.CommissionAccrualRepository;
import com.example.settlement.domain.repository.OrderSnapshotRepository;
import com.example.settlement.domain.repository.PromotionCostRepository;
import com.example.settlement.domain.seller.SellerScopeContext;
import com.example.settlement.domain.tenant.TenantContext;
import com.example.settlement.infrastructure.event.OrderPlacedEvent;
import com.example.settlement.infrastructure.event.OrderPlacedSnapshotConsumer;
import com.example.settlement.infrastructure.event.PaymentCompletedAccrualConsumer;
import com.example.settlement.infrastructure.event.PaymentEvent;
import com.example.settlement.infrastructure.event.PaymentRefundedReversalConsumer;
import org.junit.jupiter.api.AfterEach;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * TASK-BE-592 against real Postgres (Testcontainers, V7 applied): a coupon-discounted order's
 * placed → completed → partial refund → full refund round trip, asserted through the repositories.
 *
 * <p>What only a database can prove here: the V7 snapshot columns round-trip, the
 * {@code promotion_cost} table accepts the COST / REVERSAL rows the service writes
 * ({@code ck_promotion_cost_sign}), and {@code ck_commission_accrual_split} still holds on every
 * reversal computed against the captured denominator.
 *
 * <p>Order: 15,000 × 2 = 30,000 gross at the 10% platform default, coupon discount 5,000, captured 25,000.
 */
@SpringBootTest(classes = SettlementServiceApplication.class,
        properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1)
class SettlementPromotionCostIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("settlement_db")
            .withUsername("settlement_user")
            .withPassword("settlement_pass");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("settlement.commission.default-rate-bps", () -> "1000");
    }

    @Autowired
    private OrderPlacedSnapshotConsumer orderPlacedConsumer;
    @Autowired
    private PaymentCompletedAccrualConsumer paymentCompletedConsumer;
    @Autowired
    private PaymentRefundedReversalConsumer paymentRefundedConsumer;
    @Autowired
    private CommissionAccrualRepository accrualRepository;
    @Autowired
    private PromotionCostRepository promotionCostRepository;
    @Autowired
    private OrderSnapshotRepository snapshotRepository;

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        SellerScopeContext.clear();
    }

    private SellerBalance balance(String sellerId) {
        TenantContext.set("tenantA");
        try {
            return accrualRepository.sellerBalance(sellerId);
        } finally {
            TenantContext.clear();
        }
    }

    private static long total(List<PromotionCost> rows) {
        return rows.stream().mapToLong(PromotionCost::amountMinor).sum();
    }

    @Test
    void couponOrder_roundTrip_commissionOnPreDiscountGross_promotionCostSeparate_bothNetToZero() {
        orderPlacedConsumer.handle(new OrderPlacedEvent("00000000-0000-0000-0000-000000005921", "OrderPlaced",
                "tenantA", new OrderPlacedEvent.Payload("order-592", List.of(
                        new OrderPlacedEvent.Item(15_000L, 2, "seller-592")), "coupon-592", 5_000L)));

        // The V7 snapshot columns round-trip.
        assertThat(snapshotRepository.findByOrderId("order-592")).hasValueSatisfying(s -> {
            assertThat(s.discountMinor()).isEqualTo(5_000L);
            assertThat(s.couponId()).isEqualTo("coupon-592");
        });

        paymentCompletedConsumer.handle(new PaymentEvent("00000000-0000-0000-0000-000000005922", "PaymentCompleted",
                new PaymentEvent.Payload("order-592", "pay-592", 25_000L, null, "2026-09-15T00:00:00Z", null)));

        SellerBalance afterAccrual = balance("seller-592");
        assertThat(afterAccrual.platformCommissionMinor()).isEqualTo(3_000L);
        assertThat(afterAccrual.accruedNetMinor()).isEqualTo(27_000L);
        List<PromotionCost> costs = promotionCostRepository.findByOrderId("order-592");
        assertThat(costs).singleElement().satisfies(c -> {
            assertThat(c.type()).isEqualTo(PromotionCostType.COST);
            assertThat(c.amountMinor()).isEqualTo(5_000L);
            assertThat(c.tenantId()).isEqualTo("tenantA");
        });

        // Partial refund 10,000 of the 25,000 captured (40%): gross 12,000 → commission 1,200, net 10,800;
        // promotion cost 2,000.
        paymentRefundedConsumer.handle(new PaymentEvent("00000000-0000-0000-0000-000000005923", "PaymentRefunded",
                new PaymentEvent.Payload("order-592", "refund-592a", 10_000L, false, null, "2026-09-15T01:00:00Z")));

        SellerBalance afterPartial = balance("seller-592");
        assertThat(afterPartial.platformCommissionMinor()).isEqualTo(1_800L);
        assertThat(afterPartial.accruedNetMinor()).isEqualTo(16_200L);
        assertThat(total(promotionCostRepository.findByOrderId("order-592"))).isEqualTo(3_000L);

        // Final refund of the remaining 15,000 → both ledgers exactly zero.
        paymentRefundedConsumer.handle(new PaymentEvent("00000000-0000-0000-0000-000000005924", "PaymentRefunded",
                new PaymentEvent.Payload("order-592", "refund-592b", 15_000L, true, null, "2026-09-15T02:00:00Z")));

        SellerBalance afterFull = balance("seller-592");
        assertThat(afterFull.platformCommissionMinor()).isZero();
        assertThat(afterFull.accruedNetMinor()).isZero();
        List<PromotionCost> finalCosts = promotionCostRepository.findByOrderId("order-592");
        assertThat(finalCosts).hasSize(3);
        assertThat(total(finalCosts)).isZero();
        assertThat(finalCosts).filteredOn(c -> c.type() == PromotionCostType.REVERSAL)
                .allSatisfy(c -> assertThat(c.reversesCostId()).isNotNull());
    }

    @Test
    void orderWithoutCoupon_writesNoPromotionCost() {
        orderPlacedConsumer.handle(new OrderPlacedEvent("00000000-0000-0000-0000-000000005925", "OrderPlaced",
                "tenantA", new OrderPlacedEvent.Payload("order-592-plain", List.of(
                        new OrderPlacedEvent.Item(30_000L, 1, "seller-592-plain")))));
        paymentCompletedConsumer.handle(new PaymentEvent("00000000-0000-0000-0000-000000005926", "PaymentCompleted",
                new PaymentEvent.Payload("order-592-plain", "pay-592-plain", 30_000L, null, null, null)));

        assertThat(promotionCostRepository.findByOrderId("order-592-plain")).isEmpty();
        assertThat(balance("seller-592-plain").accruedNetMinor()).isEqualTo(27_000L);
    }
}
