package com.example.settlement;

import com.example.settlement.domain.model.CommissionAccrual;
import com.example.common.page.PageQuery;
import com.example.common.page.PageResult;
import com.example.settlement.domain.model.SellerBalance;
import com.example.settlement.domain.repository.CommissionAccrualRepository;
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
 * Full settlement round-trip IT — V1 Flyway applied against real Postgres
 * (Testcontainers). Drives the consumers' {@code handle()} directly (deterministic,
 * no Kafka traffic) and asserts through the repositories.
 *
 * <p>Covered: placed → completed → accrual → refunded → net-zero (AC-3/AC-5),
 * idempotent replay (AC-6), <b>tenant derived from the snapshot</b> (AC-7), the
 * cross-tenant leak regression (M6 / AC-7), and cross-seller scope isolation (AC-8).
 *
 * <p>★ Pinned to {@code SettlementServiceApplication.class} to avoid the
 * multiple-{@code @SpringBootConfiguration} ambiguity (the {@code TestSettlement…}
 * slice bootstrap is also on the test classpath). Carries {@code @Tag("integration")}
 * so it is excluded from the Docker-free {@code :check} (runs with {@code -PrunIntegration}).
 */
@SpringBootTest(classes = SettlementServiceApplication.class,
        properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1)
class SettlementLedgerIntegrationTest {

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
        // Platform default 10% so accrual is non-trivial in the round-trip.
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

    private static final PageQuery PAGE = PageQuery.of(0, 50, "occurredAt", "DESC");

    @AfterEach
    void clearContext() {
        TenantContext.clear();
        SellerScopeContext.clear();
    }

    @Test
    void full_round_trip_accrues_then_nets_to_zero_on_refund() {
        orderPlacedConsumer.handle(new OrderPlacedEvent("e-placed-1", "OrderPlaced", "tenantA",
                new OrderPlacedEvent.Payload("order-1", List.of(
                        new OrderPlacedEvent.Item(15_000L, 2, "seller-1"))))); // gross 30000

        paymentCompletedConsumer.handle(new PaymentEvent("e-paid-1", "PaymentCompleted",
                new PaymentEvent.Payload("order-1", "pay-1", "2026-06-13T00:00:00Z", null)));

        TenantContext.set("tenantA");
        SellerBalance afterAccrual = accrualRepository.sellerBalance("seller-1");
        assertThat(afterAccrual.platformCommissionMinor()).isEqualTo(3_000L);
        assertThat(afterAccrual.accruedNetMinor()).isEqualTo(27_000L);
        TenantContext.clear();

        // Refund → reversal nets back to zero.
        paymentRefundedConsumer.handle(new PaymentEvent("e-refund-1", "PaymentRefunded",
                new PaymentEvent.Payload("order-1", "refund-1", null, "2026-06-13T01:00:00Z")));

        TenantContext.set("tenantA");
        SellerBalance afterRefund = accrualRepository.sellerBalance("seller-1");
        assertThat(afterRefund.accruedNetMinor()).isZero();
        assertThat(afterRefund.platformCommissionMinor()).isZero();
        assertThat(afterRefund.accrualCount()).isEqualTo(2L); // 1 accrual + 1 reversal
    }

    @Test
    void accrual_tenant_is_derived_from_snapshot_not_payment_event() {
        orderPlacedConsumer.handle(new OrderPlacedEvent("e-placed-2", "OrderPlaced", "tenantB",
                new OrderPlacedEvent.Payload("order-2", List.of(
                        new OrderPlacedEvent.Item(10_000L, 1, "seller-9")))));
        // Payment event carries NO tenant — accrual must still land on tenantB.
        paymentCompletedConsumer.handle(new PaymentEvent("e-paid-2", "PaymentCompleted",
                new PaymentEvent.Payload("order-2", "pay-2", null, null)));

        TenantContext.set("tenantB");
        PageResult<CommissionAccrual> rows = accrualRepository.findAccruals("seller-9", null, PAGE);
        assertThat(rows.content()).isNotEmpty();
        assertThat(rows.content()).allSatisfy(r -> assertThat(r.tenantId()).isEqualTo("tenantB"));
    }

    @Test
    void cross_tenant_accruals_do_not_leak_M6() {
        orderPlacedConsumer.handle(new OrderPlacedEvent("e-placed-3", "OrderPlaced", "tenantA",
                new OrderPlacedEvent.Payload("order-3", List.of(
                        new OrderPlacedEvent.Item(20_000L, 1, "shared-seller")))));
        paymentCompletedConsumer.handle(new PaymentEvent("e-paid-3", "PaymentCompleted",
                new PaymentEvent.Payload("order-3", "pay-3", null, null)));

        // tenantB operator must NOT see tenantA's accrual for the same seller id.
        TenantContext.set("tenantB");
        PageResult<CommissionAccrual> seenByB = accrualRepository.findAccruals("shared-seller", null, PAGE);
        assertThat(seenByB.content()).isEmpty();
        assertThat(accrualRepository.sellerBalance("shared-seller").accrualCount()).isZero();

        // tenantA operator does see it.
        TenantContext.set("tenantA");
        PageResult<CommissionAccrual> seenByA = accrualRepository.findAccruals("shared-seller", null, PAGE);
        assertThat(seenByA.content()).isNotEmpty();
    }

    @Test
    void seller_scope_restricts_reads_within_tenant_AC8() {
        orderPlacedConsumer.handle(new OrderPlacedEvent("e-placed-4", "OrderPlaced", "tenantC",
                new OrderPlacedEvent.Payload("order-4", List.of(
                        new OrderPlacedEvent.Item(10_000L, 1, "seller-x"),
                        new OrderPlacedEvent.Item(10_000L, 1, "seller-y")))));
        paymentCompletedConsumer.handle(new PaymentEvent("e-paid-4", "PaymentCompleted",
                new PaymentEvent.Payload("order-4", "pay-4", null, null)));

        TenantContext.set("tenantC");

        // Unrestricted (net-zero / fail-OPEN) → both sellers visible.
        SellerScopeContext.clear();
        assertThat(accrualRepository.findAccruals(null, null, PAGE).content()).hasSize(2);

        // Restricted to seller-x → only seller-x rows.
        SellerScopeContext.set("seller-x");
        PageResult<CommissionAccrual> scoped = accrualRepository.findAccruals(null, null, PAGE);
        assertThat(scoped.content()).hasSize(1);
        assertThat(scoped.content()).allSatisfy(r -> assertThat(r.sellerId()).isEqualTo("seller-x"));
    }

    @Test
    void replayed_payment_does_not_double_accrue_AC6() {
        orderPlacedConsumer.handle(new OrderPlacedEvent("e-placed-5", "OrderPlaced", "tenantD",
                new OrderPlacedEvent.Payload("order-5", List.of(
                        new OrderPlacedEvent.Item(10_000L, 1, "seller-d")))));

        PaymentEvent paid = new PaymentEvent("e-paid-5", "PaymentCompleted",
                new PaymentEvent.Payload("order-5", "pay-5", null, null));
        paymentCompletedConsumer.handle(paid);
        // Replay with a fresh event_id but the same (order_id, payment_id) — the
        // (order_id, payment_id) guard must still prevent a double accrual.
        paymentCompletedConsumer.handle(new PaymentEvent("e-paid-5-dup", "PaymentCompleted",
                new PaymentEvent.Payload("order-5", "pay-5", null, null)));

        TenantContext.set("tenantD");
        assertThat(accrualRepository.sellerBalance("seller-d").accrualCount()).isEqualTo(1L);
    }
}
