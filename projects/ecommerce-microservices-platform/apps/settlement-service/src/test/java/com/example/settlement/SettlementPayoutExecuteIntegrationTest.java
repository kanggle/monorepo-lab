package com.example.settlement;

import com.example.settlement.application.service.CloseSettlementPeriodUseCase;
import com.example.settlement.application.service.ExecuteSellerPayoutsUseCase;
import com.example.settlement.application.service.OpenSettlementPeriodUseCase;
import com.example.settlement.application.view.PayoutView;
import com.example.settlement.application.view.PeriodView;
import com.example.settlement.domain.model.AccrualType;
import com.example.settlement.domain.model.CommissionAccrual;
import com.example.settlement.domain.period.PeriodNotClosedException;
import com.example.settlement.domain.repository.CommissionAccrualRepository;
import com.example.settlement.domain.seller.SellerScopeContext;
import com.example.settlement.domain.tenant.TenantContext;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.AfterEach;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Integration tests for the payout-execute slice (TASK-BE-416 AC-2/AC-3/AC-4/AC-5/AC-6/AC-7).
 * Uses the BE-415 close path as setup (open period → seed accruals → close → execute).
 *
 * <p>Excluded from the Docker-free {@code :test} task via {@code @Tag("integration")}.
 * The Testcontainers PostgreSQL container provides a real DB; the outbox poller is
 * disabled (no real Kafka broker needed; EmbeddedKafka covers the embedded Kafka).
 */
@SpringBootTest(classes = SettlementServiceApplication.class,
        properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1)
class SettlementPayoutExecuteIntegrationTest {

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
    private OpenSettlementPeriodUseCase openPeriod;
    @Autowired
    private CloseSettlementPeriodUseCase closePeriod;
    @Autowired
    private ExecuteSellerPayoutsUseCase executePayouts;
    @Autowired
    private CommissionAccrualRepository accrualRepository;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private MeterRegistry meterRegistry;

    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant IN_WINDOW = Instant.parse("2026-06-15T00:00:00Z");

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        SellerScopeContext.clear();
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM seller_payout");
        jdbc.update("DELETE FROM settlement_period");
        jdbc.update("DELETE FROM commission_accrual");
    }

    private void seedAccrual(String id, String tenant, String seller,
                             long gross, long commission, long sellerNet) {
        accrualRepository.appendAll(List.of(new CommissionAccrual(
                id, tenant, "order-" + id, "pay-" + id, seller,
                AccrualType.ACCRUAL, gross, 1000, commission, sellerNet, IN_WINDOW)));
    }

    /** Open a period, seed accruals, close → returns the opened periodId. */
    private String openAndClose(String tenant, String sellerId, String accrualId) {
        TenantContext.set(tenant);
        seedAccrual(accrualId, tenant, sellerId, 30_000L, 3_000L, 27_000L);
        PeriodView opened = openPeriod.open(tenant, FROM, TO);
        closePeriod.close(opened.periodId(), tenant, "operator-1");
        return opened.periodId();
    }

    // ── AC-2: close → execute → PAID + reference + paidAt ────────────────────

    @Test
    void close_then_execute_flips_pending_to_paid_with_sim_reference() {
        String periodId = openAndClose("tenantA", "seller-1", "acc-1");

        List<PayoutView> views = executePayouts.execute(periodId, "tenantA");

        assertThat(views).hasSize(1);
        PayoutView view = views.get(0);
        assertThat(view.status()).isEqualTo("PAID");
        assertThat(view.payoutReference()).isNotBlank().startsWith("SIM-");
        assertThat(view.paidAt()).isNotNull();

        // Verify DB row is actually PAID.
        Map<String, Object> row = jdbc.queryForMap(
                "SELECT status, payout_reference, paid_at FROM seller_payout WHERE period_id = ?",
                periodId);
        assertThat(row.get("status")).isEqualTo("PAID");
        assertThat((String) row.get("payout_reference")).startsWith("SIM-");
        assertThat(row.get("paid_at")).isNotNull();
    }

    // ── AC-3: idempotent re-execute — already-PAID not re-processed ───────────

    @Test
    void execute_twice_is_idempotent_reference_stable() {
        String periodId = openAndClose("tenantA", "seller-1", "acc-2");

        // C-2: snapshot the metric before the first execute so we can assert an exact delta.
        double counterBefore = getPayoutCounter("PAID");

        List<PayoutView> first = executePayouts.execute(periodId, "tenantA");
        List<PayoutView> second = executePayouts.execute(periodId, "tenantA");

        // Both runs return the same PAID status.
        assertThat(first).hasSize(1);
        assertThat(second).hasSize(1);
        assertThat(first.get(0).status()).isEqualTo("PAID");
        assertThat(second.get(0).status()).isEqualTo("PAID");

        // Reference is STABLE (the second run did not overwrite it).
        assertThat(first.get(0).payoutReference()).isEqualTo(second.get(0).payoutReference());

        // DB has exactly 1 payout row (no duplicates).
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM seller_payout WHERE period_id = ?", Long.class, periodId);
        assertThat(count).isEqualTo(1L);

        // C-2: metric must be incremented exactly ONCE (by the first execute only).
        // The second execute skips the already-PAID row — no additional counter increment.
        double counterAfter = getPayoutCounter("PAID");
        assertThat(counterAfter - counterBefore).isEqualTo(1.0);
    }

    // ── AC-4: OPEN period execute → 409 PERIOD_NOT_CLOSED ────────────────────

    @Test
    void execute_on_open_period_throws_period_not_closed_409() {
        TenantContext.set("tenantA");
        seedAccrual("acc-3", "tenantA", "seller-1", 30_000L, 3_000L, 27_000L);
        PeriodView opened = openPeriod.open("tenantA", FROM, TO);

        // Period is OPEN (not yet closed) — execute must throw 409.
        assertThatThrownBy(() -> executePayouts.execute(opened.periodId(), "tenantA"))
                .isInstanceOf(PeriodNotClosedException.class);

        // No payout rows must have been created or mutated.
        Long payoutCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM seller_payout WHERE period_id = ?",
                Long.class, opened.periodId());
        assertThat(payoutCount).isEqualTo(0L);
    }

    // ── AC-5: seller-scope read isolation ────────────────────────────────────

    @Test
    void list_seller_scope_restricted_operator_sees_only_own_payouts() {
        // Two sellers in tenantA.
        TenantContext.set("tenantA");
        seedAccrual("a1", "tenantA", "seller-A", 30_000L, 3_000L, 27_000L);
        seedAccrual("b1", "tenantA", "seller-B", 20_000L, 2_000L, 18_000L);
        PeriodView opened = openPeriod.open("tenantA", FROM, TO);
        closePeriod.close(opened.periodId(), "tenantA", "operator-1");
        executePayouts.execute(opened.periodId(), "tenantA");

        // Restricted operator for seller-A.
        SellerScopeContext.set("seller-A");
        List<PayoutView> views = executePayouts.list(opened.periodId(), "tenantA");
        SellerScopeContext.clear();

        assertThat(views).hasSize(1);
        assertThat(views.get(0).sellerId()).isEqualTo("seller-A");
    }

    @Test
    void list_unrestricted_operator_sees_all_payouts() {
        TenantContext.set("tenantA");
        seedAccrual("a2", "tenantA", "seller-A", 30_000L, 3_000L, 27_000L);
        seedAccrual("b2", "tenantA", "seller-B", 20_000L, 2_000L, 18_000L);
        PeriodView opened = openPeriod.open("tenantA", FROM, TO);
        closePeriod.close(opened.periodId(), "tenantA", "operator-1");

        // No seller scope → unrestricted.
        List<PayoutView> views = executePayouts.list(opened.periodId(), "tenantA");

        assertThat(views).hasSize(2);
    }

    // ── AC-6: metric settlement_payout_total{status=PAID} ─────────────────────

    @Test
    void execute_increments_settlement_payout_total_paid_metric() {
        // Reset metric baseline.
        double before = getPayoutCounter("PAID");

        TenantContext.set("tenantA");
        seedAccrual("acc-4", "tenantA", "seller-1", 30_000L, 3_000L, 27_000L);
        seedAccrual("acc-5", "tenantA", "seller-2", 20_000L, 2_000L, 18_000L);
        PeriodView opened = openPeriod.open("tenantA", FROM, TO);
        closePeriod.close(opened.periodId(), "tenantA", "operator-1");
        executePayouts.execute(opened.periodId(), "tenantA");

        double after = getPayoutCounter("PAID");
        assertThat(after - before).isGreaterThanOrEqualTo(2.0);
    }

    private double getPayoutCounter(String status) {
        Counter counter = meterRegistry.find("settlement_payout_total")
                .tag("status", status)
                .counter();
        return counter == null ? 0.0 : counter.count();
    }
}
