package com.example.settlement;

import com.example.settlement.application.event.SettlementPeriodClosedEvent;
import com.example.settlement.application.port.SettlementEventPublisher;
import com.example.settlement.application.service.CloseSettlementPeriodUseCase;
import com.example.settlement.application.service.OpenSettlementPeriodUseCase;
import com.example.settlement.application.view.PeriodView;
import com.example.settlement.domain.model.AccrualType;
import com.example.settlement.domain.model.CommissionAccrual;
import com.example.settlement.domain.repository.CommissionAccrualRepository;
import com.example.settlement.domain.tenant.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

/**
 * Period-close integration test (AC-1/AC-3/AC-4/AC-5/AC-6 — Testcontainers). Asserts
 * V2 applies, close → {@code seller_payout} PENDING + outbox row created while the
 * accrual ledger is UNCHANGED (F3), transactional rollback atomicity (a forced
 * publish failure leaves the period OPEN + 0 payouts + 0 outbox), and cross-tenant
 * isolation (M6).
 *
 * <p>★ Pinned to {@code SettlementServiceApplication.class}; {@code @Tag("integration")}
 * so it is excluded from the Docker-free {@code :test}.
 */
@SpringBootTest(classes = SettlementServiceApplication.class,
        properties = "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}")
@Tag("integration")
@Testcontainers
@EmbeddedKafka(partitions = 1)
class SettlementPeriodCloseIntegrationTest {

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
    private CommissionAccrualRepository accrualRepository;
    @Autowired
    private JdbcTemplate jdbc;

    // Spied so the real (outbox-writing) publisher runs by default, while one test can
    // force a publish failure to prove rollback atomicity (AC-5).
    @MockitoSpyBean
    private SettlementEventPublisher eventPublisher;

    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant IN_WINDOW = Instant.parse("2026-06-15T00:00:00Z");
    private static final Instant OUT_OF_WINDOW = Instant.parse("2026-07-15T00:00:00Z");

    @AfterEach
    void cleanup() {
        TenantContext.clear();
        jdbc.update("DELETE FROM outbox");
        jdbc.update("DELETE FROM seller_payout");
        jdbc.update("DELETE FROM settlement_period");
        jdbc.update("DELETE FROM commission_accrual");
    }

    private void seedAccrual(String id, String tenant, String seller, AccrualType type,
                             long gross, long commission, long sellerNet, Instant occurredAt) {
        accrualRepository.appendAll(List.of(new CommissionAccrual(
                id, tenant, "order-" + id, "pay-" + id, seller, type,
                gross, 1000, commission, sellerNet, occurredAt)));
    }

    @Test
    void close_folds_in_window_accruals_into_pending_payouts_outbox_row_accrual_unchanged() {
        TenantContext.set("tenantA");
        // seller-1: in-window accrual (counted). seller-1 also has an out-of-window accrual (excluded).
        seedAccrual("a1", "tenantA", "seller-1", AccrualType.ACCRUAL, 30_000, 3_000, 27_000, IN_WINDOW);
        seedAccrual("a2", "tenantA", "seller-1", AccrualType.ACCRUAL, 10_000, 1_000, 9_000, OUT_OF_WINDOW);
        // seller-2: in-window accrual + a partial reversal still nets positive.
        seedAccrual("b1", "tenantA", "seller-2", AccrualType.ACCRUAL, 20_000, 2_000, 18_000, IN_WINDOW);
        seedAccrual("b2", "tenantA", "seller-2", AccrualType.REVERSAL, -5_000, -500, -4_500, IN_WINDOW);

        long accrualBefore = jdbc.queryForObject("SELECT COUNT(*) FROM commission_accrual", Long.class);

        PeriodView opened = openPeriod.open("tenantA", FROM, TO);
        PeriodView closed = closePeriod.close(opened.periodId(), "tenantA", "operator-1");

        assertThat(closed.status()).isEqualTo("CLOSED");
        assertThat(closed.sellerCount()).isEqualTo(2);

        // seller_payout PENDING rows created with the in-window fold.
        List<java.util.Map<String, Object>> payouts = jdbc.queryForList(
                "SELECT seller_id, payable_net_minor, commission_minor, accrual_count, status "
                        + "FROM seller_payout WHERE period_id = ? ORDER BY seller_id", opened.periodId());
        assertThat(payouts).hasSize(2);
        assertThat(payouts).allSatisfy(r -> assertThat(r.get("status")).isEqualTo("PENDING"));
        // seller-1: only the in-window accrual (out-of-window a2 excluded by [from, to)).
        assertThat(((Number) payouts.get(0).get("payable_net_minor")).longValue()).isEqualTo(27_000L);
        assertThat(((Number) payouts.get(0).get("accrual_count")).intValue()).isEqualTo(1);
        // seller-2: 18000 - 4500 = 13500 net over 2 rows.
        assertThat(((Number) payouts.get(1).get("payable_net_minor")).longValue()).isEqualTo(13_500L);
        assertThat(((Number) payouts.get(1).get("accrual_count")).intValue()).isEqualTo(2);

        // Exactly one outbox row for settlement.period.closed.v1 (co-committed, AC-5/AC-6).
        Long outboxRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM outbox WHERE event_type = ?", Long.class,
                SettlementPeriodClosedEvent.EVENT_TYPE);
        assertThat(outboxRows).isEqualTo(1L);
        String payload = jdbc.queryForObject(
                "SELECT payload FROM outbox WHERE event_type = ?", String.class,
                SettlementPeriodClosedEvent.EVENT_TYPE);
        assertThat(payload).contains("\"tenant_id\":\"tenantA\"")
                .contains("\"seller_count\":2")
                .contains("\"payable_net_minor\":27000");
        org.mockito.Mockito.verify(eventPublisher).publishPeriodClosed(org.mockito.ArgumentMatchers.any());

        // Accrual ledger UNCHANGED (F3) — same row count before/after the close.
        long accrualAfter = jdbc.queryForObject("SELECT COUNT(*) FROM commission_accrual", Long.class);
        assertThat(accrualAfter).isEqualTo(accrualBefore);
    }

    @Test
    void close_skips_net_zero_seller_decision7() {
        TenantContext.set("tenantA");
        seedAccrual("p1", "tenantA", "seller-pos", AccrualType.ACCRUAL, 30_000, 3_000, 27_000, IN_WINDOW);
        // seller-zero fully reversed → payable_net = 0 → no payout row.
        seedAccrual("z1", "tenantA", "seller-zero", AccrualType.ACCRUAL, 10_000, 1_000, 9_000, IN_WINDOW);
        seedAccrual("z2", "tenantA", "seller-zero", AccrualType.REVERSAL, -10_000, -1_000, -9_000, IN_WINDOW);

        PeriodView opened = openPeriod.open("tenantA", FROM, TO);
        PeriodView closed = closePeriod.close(opened.periodId(), "tenantA", "operator-1");

        assertThat(closed.sellerCount()).isEqualTo(1);
        Long payoutCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM seller_payout WHERE period_id = ?", Long.class, opened.periodId());
        assertThat(payoutCount).isEqualTo(1L);
        Long zeroSellerRows = jdbc.queryForObject(
                "SELECT COUNT(*) FROM seller_payout WHERE seller_id = 'seller-zero'", Long.class);
        assertThat(zeroSellerRows).isEqualTo(0L);
    }

    @Test
    void rollback_atomicity_forced_failure_leaves_period_open_no_payouts_no_outbox() {
        TenantContext.set("tenantA");
        seedAccrual("r1", "tenantA", "seller-1", AccrualType.ACCRUAL, 30_000, 3_000, 27_000, IN_WINDOW);

        // Force the in-transaction publish to fail → the whole close transaction rolls back.
        doThrow(new RuntimeException("forced outbox failure"))
                .when(eventPublisher).publishPeriodClosed(org.mockito.ArgumentMatchers.any());

        PeriodView opened = openPeriod.open("tenantA", FROM, TO);

        assertThatThrownBy(() -> closePeriod.close(opened.periodId(), "tenantA", "operator-1"))
                .isInstanceOf(RuntimeException.class);

        // Period stays OPEN, no payout rows, no outbox rows (AC-5).
        String status = jdbc.queryForObject(
                "SELECT status FROM settlement_period WHERE period_id = ?", String.class, opened.periodId());
        assertThat(status).isEqualTo("OPEN");
        Long payoutCount = jdbc.queryForObject(
                "SELECT COUNT(*) FROM seller_payout WHERE period_id = ?", Long.class, opened.periodId());
        assertThat(payoutCount).isEqualTo(0L);
        Long outboxCount = jdbc.queryForObject("SELECT COUNT(*) FROM outbox", Long.class);
        assertThat(outboxCount).isEqualTo(0L);
    }

    @Test
    void cross_tenant_period_close_resolves_404_M6() {
        TenantContext.set("tenantA");
        PeriodView opened = openPeriod.open("tenantA", FROM, TO);

        // tenantB operator cannot close tenantA's period (tenant-scoped → 404).
        assertThatThrownBy(() -> closePeriod.close(opened.periodId(), "tenantB", "operator-b"))
                .isInstanceOf(com.example.settlement.domain.period.PeriodNotFoundException.class);
    }
}
