package com.example.settlement.application.service;

import com.example.settlement.application.exception.SellerScopeForbiddenException;
import com.example.settlement.application.port.SellerPayoutPort;
import com.example.settlement.application.port.SellerPayoutPort.PayoutExecutionResult;
import com.example.settlement.application.port.SettlementMetricsPort;
import com.example.settlement.application.view.PayoutView;
import com.example.settlement.domain.payout.PayoutStatus;
import com.example.settlement.domain.payout.SellerPayout;
import com.example.settlement.domain.period.PeriodNotClosedException;
import com.example.settlement.domain.period.PeriodNotFoundException;
import com.example.settlement.domain.period.PeriodStatus;
import com.example.settlement.domain.period.SettlementPeriod;
import com.example.settlement.domain.repository.SellerPayoutRepository;
import com.example.settlement.domain.repository.SettlementPeriodRepository;
import com.example.settlement.domain.seller.SellerScopeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link ExecuteSellerPayoutsUseCase} (TASK-BE-416 AC-2/AC-3/AC-4/AC-6):
 * PENDING→PAID transition, idempotent re-run, OPEN period 409, metric increments,
 * seller-scope filter.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.STRICT_STUBS)
class ExecuteSellerPayoutsUseCaseTest {

    private static final String TENANT = "tenantA";
    private static final String PERIOD_ID = "p-1";
    private static final Instant FROM = Instant.parse("2026-06-01T00:00:00Z");
    private static final Instant TO = Instant.parse("2026-07-01T00:00:00Z");
    private static final Instant NOW = Instant.parse("2026-07-02T12:00:00Z");

    @Mock
    private SettlementPeriodRepository periodRepository;
    @Mock
    private SellerPayoutRepository payoutRepository;
    @Mock
    private SellerPayoutPort payoutPort;
    @Mock
    private SettlementMetricsPort metrics;

    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    private ExecuteSellerPayoutsUseCase useCase() {
        return new ExecuteSellerPayoutsUseCase(
                periodRepository, payoutRepository, payoutPort, metrics, clock);
    }

    @AfterEach
    void clearSellerScope() {
        SellerScopeContext.clear();
    }

    private static SettlementPeriod closedPeriod() {
        SettlementPeriod p = SettlementPeriod.open(PERIOD_ID, TENANT, FROM, TO);
        p.close(Instant.parse("2026-07-01T09:00:00Z"), "operator-1", 2);
        return p;
    }

    private static SettlementPeriod openPeriod() {
        return SettlementPeriod.open(PERIOD_ID, TENANT, FROM, TO);
    }

    private static SellerPayout pendingPayout(String payoutId, String sellerId) {
        return SellerPayout.pending(payoutId, PERIOD_ID, TENANT, sellerId, 27_000L, 3_000L, 1);
    }

    // ── execute: PENDING → PAID transition ────────────────────────────────────

    @Test
    void execute_pending_payouts_transition_to_paid() {
        SellerPayout payout1 = pendingPayout("po-1", "seller-1");
        SellerPayout payout2 = pendingPayout("po-2", "seller-2");

        given(periodRepository.findById(PERIOD_ID, TENANT)).willReturn(Optional.of(closedPeriod()));
        given(payoutRepository.findByPeriodAndTenant(PERIOD_ID, TENANT))
                .willReturn(List.of(payout1, payout2));
        given(payoutPort.execute(payout1)).willReturn(PayoutExecutionResult.paid("SIM-p-1-seller-1-uuid1"));
        given(payoutPort.execute(payout2)).willReturn(PayoutExecutionResult.paid("SIM-p-1-seller-2-uuid2"));
        given(payoutRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        List<PayoutView> views = useCase().execute(PERIOD_ID, TENANT);

        // Both payouts now PAID.
        assertThat(views).hasSize(2);
        assertThat(views).allSatisfy(v -> assertThat(v.status()).isEqualTo("PAID"));
        assertThat(payout1.status()).isEqualTo(PayoutStatus.PAID);
        assertThat(payout1.payoutReference()).isEqualTo("SIM-p-1-seller-1-uuid1");
        assertThat(payout2.status()).isEqualTo(PayoutStatus.PAID);

        // Metric recorded twice (once per payout).
        verify(metrics, times(2)).recordPayoutExecuted("PAID");
        verify(payoutRepository, times(2)).save(any());
    }

    // ── execute: idempotency — already-PAID skipped ────────────────────────────

    @Test
    void execute_idempotent_already_paid_rows_skipped() {
        SellerPayout paid = pendingPayout("po-1", "seller-1");
        paid.markPaid("SIM-ref-001", Instant.parse("2026-07-01T10:00:00Z"));

        SellerPayout pending = pendingPayout("po-2", "seller-2");

        given(periodRepository.findById(PERIOD_ID, TENANT)).willReturn(Optional.of(closedPeriod()));
        given(payoutRepository.findByPeriodAndTenant(PERIOD_ID, TENANT))
                .willReturn(List.of(paid, pending));
        given(payoutPort.execute(pending)).willReturn(PayoutExecutionResult.paid("SIM-ref-002"));
        given(payoutRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        List<PayoutView> views = useCase().execute(PERIOD_ID, TENANT);

        // Both rows returned; only the remaining PENDING was processed.
        assertThat(views).hasSize(2);
        verify(payoutPort, never()).execute(paid);   // already-PAID → skipped
        verify(payoutPort, times(1)).execute(pending);
        verify(metrics, times(1)).recordPayoutExecuted("PAID"); // only 1 new PAID
    }

    // ── execute: OPEN period → 409 PERIOD_NOT_CLOSED ─────────────────────────

    @Test
    void execute_open_period_throws_period_not_closed_409() {
        given(periodRepository.findById(PERIOD_ID, TENANT)).willReturn(Optional.of(openPeriod()));

        assertThatThrownBy(() -> useCase().execute(PERIOD_ID, TENANT))
                .isInstanceOf(PeriodNotClosedException.class);

        verify(payoutRepository, never()).findByPeriodAndTenant(anyString(), anyString());
        verify(payoutPort, never()).execute(any());
        verify(metrics, never()).recordPayoutExecuted(anyString());
    }

    // ── execute: period not found → 404 ───────────────────────────────────────

    @Test
    void execute_period_not_found_throws_period_not_found() {
        given(periodRepository.findById(PERIOD_ID, TENANT)).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().execute(PERIOD_ID, TENANT))
                .isInstanceOf(PeriodNotFoundException.class);

        verify(payoutRepository, never()).findByPeriodAndTenant(anyString(), anyString());
    }

    // ── execute: empty PENDING rows (all already PAID) → 200 no changes ───────

    @Test
    void execute_all_already_paid_returns_empty_processed_list() {
        SellerPayout paid = pendingPayout("po-1", "seller-1");
        paid.markPaid("SIM-ref-old", Instant.parse("2026-07-01T10:00:00Z"));

        given(periodRepository.findById(PERIOD_ID, TENANT)).willReturn(Optional.of(closedPeriod()));
        given(payoutRepository.findByPeriodAndTenant(PERIOD_ID, TENANT)).willReturn(List.of(paid));

        List<PayoutView> views = useCase().execute(PERIOD_ID, TENANT);

        // 1 row returned (the PAID one), no port call, no metric.
        assertThat(views).hasSize(1);
        assertThat(views.get(0).status()).isEqualTo("PAID");
        verify(payoutPort, never()).execute(any());
        verify(metrics, never()).recordPayoutExecuted(anyString());
    }

    // ── execute: FAILED outcome increments FAILED metric ──────────────────────

    @Test
    void execute_failed_outcome_increments_failed_metric() {
        SellerPayout payout = pendingPayout("po-1", "seller-1");

        given(periodRepository.findById(PERIOD_ID, TENANT)).willReturn(Optional.of(closedPeriod()));
        given(payoutRepository.findByPeriodAndTenant(PERIOD_ID, TENANT)).willReturn(List.of(payout));
        given(payoutPort.execute(payout)).willReturn(PayoutExecutionResult.failed("bank unreachable"));
        given(payoutRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        List<PayoutView> views = useCase().execute(PERIOD_ID, TENANT);

        assertThat(views).hasSize(1);
        assertThat(views.get(0).status()).isEqualTo("FAILED");
        assertThat(payout.status()).isEqualTo(PayoutStatus.FAILED);
        verify(metrics).recordPayoutExecuted("FAILED");
    }

    // ── list: seller-scope ABAC filters payout rows ───────────────────────────

    @Test
    void list_seller_scoped_operator_sees_only_own_seller_rows() {
        SellerPayout own = pendingPayout("po-1", "seller-A");
        SellerPayout other = pendingPayout("po-2", "seller-B");

        given(periodRepository.findById(PERIOD_ID, TENANT)).willReturn(Optional.of(closedPeriod()));
        given(payoutRepository.findByPeriodAndTenant(PERIOD_ID, TENANT))
                .willReturn(List.of(own, other));

        SellerScopeContext.set("seller-A");
        List<PayoutView> views = useCase().list(PERIOD_ID, TENANT);

        // seller-scope restricted to seller-A: only own row visible.
        assertThat(views).hasSize(1);
        assertThat(views.get(0).sellerId()).isEqualTo("seller-A");
    }

    @Test
    void list_unrestricted_operator_sees_all_rows() {
        SellerPayout p1 = pendingPayout("po-1", "seller-A");
        SellerPayout p2 = pendingPayout("po-2", "seller-B");

        given(periodRepository.findById(PERIOD_ID, TENANT)).willReturn(Optional.of(closedPeriod()));
        given(payoutRepository.findByPeriodAndTenant(PERIOD_ID, TENANT))
                .willReturn(List.of(p1, p2));

        // No seller scope set → unrestricted (fail-OPEN).
        List<PayoutView> views = useCase().list(PERIOD_ID, TENANT);

        assertThat(views).hasSize(2);
    }

    @Test
    void list_period_not_found_throws_period_not_found() {
        given(periodRepository.findById(PERIOD_ID, TENANT)).willReturn(Optional.empty());

        assertThatThrownBy(() -> useCase().list(PERIOD_ID, TENANT))
                .isInstanceOf(PeriodNotFoundException.class);
    }

    // ── list: C-1 cross-seller → 404 SellerScopeForbiddenException ──────────

    @Test
    void list_cross_seller_restricted_operator_throws_seller_scope_forbidden_404() {
        // Period has a payout for seller-B only; caller is restricted to seller-A.
        SellerPayout other = pendingPayout("po-2", "seller-B");

        given(periodRepository.findById(PERIOD_ID, TENANT)).willReturn(Optional.of(closedPeriod()));
        given(payoutRepository.findByPeriodAndTenant(PERIOD_ID, TENANT))
                .willReturn(List.of(other));

        SellerScopeContext.set("seller-A");
        assertThatThrownBy(() -> useCase().list(PERIOD_ID, TENANT))
                .isInstanceOf(SellerScopeForbiddenException.class);
    }

    @Test
    void list_empty_period_restricted_operator_returns_empty_list() {
        // Period has zero payout rows (genuinely empty) — any caller gets [].
        given(periodRepository.findById(PERIOD_ID, TENANT)).willReturn(Optional.of(closedPeriod()));
        given(payoutRepository.findByPeriodAndTenant(PERIOD_ID, TENANT))
                .willReturn(List.of());

        SellerScopeContext.set("seller-A");
        List<PayoutView> views = useCase().list(PERIOD_ID, TENANT);

        assertThat(views).isEmpty();
    }
}
