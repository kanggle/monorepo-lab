package com.example.settlement.application.service;

import com.example.settlement.application.exception.SnapshotNotFoundException;
import com.example.settlement.domain.model.AccrualType;
import com.example.settlement.domain.model.CommissionAccrual;
import com.example.settlement.domain.model.CommissionRate;
import com.example.settlement.domain.model.OrderSnapshot;
import com.example.settlement.domain.model.OrderSnapshotLine;
import com.example.settlement.domain.repository.CommissionAccrualRepository;
import com.example.settlement.domain.repository.OrderSnapshotRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettlementServiceTest {

    @Mock
    private OrderSnapshotRepository snapshotRepository;
    @Mock
    private CommissionAccrualRepository accrualRepository;
    @Mock
    private CommissionRateResolver rateResolver;
    @InjectMocks
    private SettlementService service;

    private static final Instant NOW = Instant.parse("2026-06-13T00:00:00Z");

    @Test
    void recordSnapshot_upserts_snapshot() {
        service.recordSnapshot(new RecordOrderSnapshotCommand(
                "order-1", "tenantA", List.of(new OrderSnapshotLine("seller-1", 30_000L))));

        ArgumentCaptor<OrderSnapshot> captor = ArgumentCaptor.forClass(OrderSnapshot.class);
        verify(snapshotRepository).upsert(captor.capture());
        assertThat(captor.getValue().orderId()).isEqualTo("order-1");
        assertThat(captor.getValue().tenantId()).isEqualTo("tenantA");
        assertThat(captor.getValue().lines()).hasSize(1);
    }

    @Test
    void accrue_splits_each_line_and_appends_accrual_rows_with_snapshot_tenant() {
        when(accrualRepository.existsAccrualFor("order-1", "pay-1")).thenReturn(false);
        when(snapshotRepository.findByOrderId("order-1")).thenReturn(java.util.Optional.of(
                new OrderSnapshot("order-1", "tenantA", List.of(
                        new OrderSnapshotLine("seller-1", 30_000L),
                        new OrderSnapshotLine("seller-2", 10_000L)))));
        when(rateResolver.resolve("tenantA", "seller-1")).thenReturn(CommissionRate.sellerOverride(1000));
        when(rateResolver.resolve("tenantA", "seller-2")).thenReturn(CommissionRate.platformDefault(0));

        service.accrue(new AccruePaymentCommand("order-1", "pay-1", NOW));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CommissionAccrual>> captor = ArgumentCaptor.forClass(List.class);
        verify(accrualRepository).appendAll(captor.capture());
        List<CommissionAccrual> rows = captor.getValue();
        assertThat(rows).hasSize(2);
        // AC-7: tenant derived from the snapshot, not from any payment field.
        assertThat(rows).allSatisfy(r -> assertThat(r.tenantId()).isEqualTo("tenantA"));
        // seller-1: 10% of 30000 = 3000 commission, 27000 net
        CommissionAccrual s1 = rows.stream().filter(r -> r.sellerId().equals("seller-1")).findFirst().orElseThrow();
        assertThat(s1.type()).isEqualTo(AccrualType.ACCRUAL);
        assertThat(s1.commissionMinor()).isEqualTo(3_000L);
        assertThat(s1.sellerNetMinor()).isEqualTo(27_000L);
        // seller-2: default 0% → net-zero degrade (AC-9)
        CommissionAccrual s2 = rows.stream().filter(r -> r.sellerId().equals("seller-2")).findFirst().orElseThrow();
        assertThat(s2.commissionMinor()).isZero();
        assertThat(s2.sellerNetMinor()).isEqualTo(10_000L);
    }

    @Test
    void accrue_is_idempotent_on_order_payment_key() {
        when(accrualRepository.existsAccrualFor("order-1", "pay-1")).thenReturn(true);

        service.accrue(new AccruePaymentCommand("order-1", "pay-1", NOW));

        verify(snapshotRepository, never()).findByOrderId(anyString());
        verify(accrualRepository, never()).appendAll(any());
    }

    @Test
    void accrue_raises_when_snapshot_missing_F2() {
        when(accrualRepository.existsAccrualFor("order-x", "pay-1")).thenReturn(false);
        when(snapshotRepository.findByOrderId("order-x")).thenReturn(java.util.Optional.empty());

        assertThatThrownBy(() -> service.accrue(new AccruePaymentCommand("order-x", "pay-1", NOW)))
                .isInstanceOf(SnapshotNotFoundException.class);
        verify(accrualRepository, never()).appendAll(any());
    }

    @SuppressWarnings("unchecked")
    private List<CommissionAccrual> captureAppended() {
        ArgumentCaptor<List<CommissionAccrual>> captor = ArgumentCaptor.forClass(List.class);
        verify(accrualRepository).appendAll(captor.capture());
        return captor.getValue();
    }

    @Test
    void reverse_fullyRefunded_negates_each_accrual_to_exactly_zero() {
        when(accrualRepository.findAccrualsByOrderId("order-1")).thenReturn(List.of(
                new CommissionAccrual("a1", "tenantA", "order-1", "pay-1", "seller-1",
                        AccrualType.ACCRUAL, 30_000L, 1000, 3_000L, 27_000L, NOW)));
        when(accrualRepository.findReversalsByOrderId("order-1")).thenReturn(List.of());

        // refundAmount == accruedGross, fullyRefunded == true
        service.reverse(new ReversePaymentCommand("order-1", "refund-1", 30_000L, true, NOW));

        List<CommissionAccrual> reversals = captureAppended();
        assertThat(reversals).hasSize(1);
        CommissionAccrual rev = reversals.get(0);
        assertThat(rev.type()).isEqualTo(AccrualType.REVERSAL);
        assertThat(rev.grossMinor()).isEqualTo(-30_000L);
        assertThat(rev.commissionMinor()).isEqualTo(-3_000L);
        assertThat(rev.sellerNetMinor()).isEqualTo(-27_000L);
        assertThat(rev.paymentId()).isEqualTo("refund-1");
        assertThat(rev.reversesAccrualId()).isEqualTo("a1");
        // each accrual nets to exactly zero per field
        assertThat(30_000L + rev.grossMinor()).isZero();
        assertThat(3_000L + rev.commissionMinor()).isZero();
        assertThat(27_000L + rev.sellerNetMinor()).isZero();
    }

    @Test
    void reverse_single_partial_reverses_half_per_line_with_split_invariant() {
        // accruedGross = 40000, refundAmount = 20000 → fraction 1/2
        when(accrualRepository.findAccrualsByOrderId("order-1")).thenReturn(List.of(
                new CommissionAccrual("a1", "tenantA", "order-1", "pay-1", "seller-1",
                        AccrualType.ACCRUAL, 30_000L, 1000, 3_000L, 27_000L, NOW),
                new CommissionAccrual("a2", "tenantA", "order-1", "pay-1", "seller-2",
                        AccrualType.ACCRUAL, 10_000L, 1000, 1_000L, 9_000L, NOW)));
        when(accrualRepository.findReversalsByOrderId("order-1")).thenReturn(List.of());

        service.reverse(new ReversePaymentCommand("order-1", "refund-1", 20_000L, false, NOW));

        List<CommissionAccrual> reversals = captureAppended();
        assertThat(reversals).hasSize(2);
        // a1: portion = round(30000 × 20000 / 40000) = 15000 → split @1000bps = (1500, 13500)
        CommissionAccrual r1 = reversals.stream().filter(r -> "a1".equals(r.reversesAccrualId()))
                .findFirst().orElseThrow();
        assertThat(r1.grossMinor()).isEqualTo(-15_000L);
        assertThat(r1.commissionMinor()).isEqualTo(-1_500L);
        assertThat(r1.sellerNetMinor()).isEqualTo(-13_500L);
        // a2: portion = round(10000 × 20000 / 40000) = 5000 → split @1000bps = (500, 4500)
        CommissionAccrual r2 = reversals.stream().filter(r -> "a2".equals(r.reversesAccrualId()))
                .findFirst().orElseThrow();
        assertThat(r2.grossMinor()).isEqualTo(-5_000L);
        assertThat(r2.commissionMinor()).isEqualTo(-500L);
        assertThat(r2.sellerNetMinor()).isEqualTo(-4_500L);
        // AC-5: per-row split invariant commission + sellerNet == gross on every reversal row.
        assertThat(reversals).allSatisfy(r ->
                assertThat(r.commissionMinor() + r.sellerNetMinor()).isEqualTo(r.grossMinor()));
    }

    @Test
    void reverse_two_partials_summing_to_full_net_exactly_zero_with_rounding_residue() {
        // gross 30001 @ 1500bps → commission = round(30001 × 1500 / 10000) = round(4500.15) = 4500,
        // sellerNet = 25501. accruedGross = 30001.
        CommissionAccrual a1 = new CommissionAccrual("a1", "tenantA", "order-1", "pay-1", "seller-1",
                AccrualType.ACCRUAL, 30_001L, 1500, 4_500L, 25_501L, NOW);
        when(accrualRepository.findAccrualsByOrderId("order-1")).thenReturn(List.of(a1));
        when(accrualRepository.findReversalsByOrderId("order-1")).thenReturn(List.of());

        // 1st partial: refund 10000 (≈1/3). portion = round(30001 × 10000 / 30001) = 10000
        // → split @1500bps: commission = round(10000 × 1500/10000) = 1500, sellerNet = 8500.
        service.reverse(new ReversePaymentCommand("order-1", "refund-1", 10_000L, false, NOW));
        List<CommissionAccrual> firstReversals = captureAppended();
        assertThat(firstReversals).hasSize(1);
        CommissionAccrual r1 = firstReversals.get(0);
        assertThat(r1.grossMinor()).isEqualTo(-10_000L);
        assertThat(r1.commissionMinor()).isEqualTo(-1_500L);
        assertThat(r1.sellerNetMinor()).isEqualTo(-8_500L);

        // 2nd refund closes the payment out: fullyRefunded = true. The repo now returns
        // the 1st partial's reversal row, so the exact-remaining reversal absorbs the drift.
        org.mockito.Mockito.reset(accrualRepository);
        when(accrualRepository.findAccrualsByOrderId("order-1")).thenReturn(List.of(a1));
        when(accrualRepository.findReversalsByOrderId("order-1")).thenReturn(List.of(r1));

        service.reverse(new ReversePaymentCommand("order-1", "refund-2", 20_001L, true, NOW));
        List<CommissionAccrual> secondReversals = captureAppended();
        assertThat(secondReversals).hasSize(1);
        CommissionAccrual r2 = secondReversals.get(0);
        // exact remaining: gross 30001 − 10000 = 20001; commission 4500 − 1500 = 3000; net 25501 − 8500 = 17001
        assertThat(r2.grossMinor()).isEqualTo(-20_001L);
        assertThat(r2.commissionMinor()).isEqualTo(-3_000L);
        assertThat(r2.sellerNetMinor()).isEqualTo(-17_001L);

        // AC-6: after both refunds the accrual nets to EXACTLY zero per field.
        assertThat(a1.grossMinor() + r1.grossMinor() + r2.grossMinor()).isZero();
        assertThat(a1.commissionMinor() + r1.commissionMinor() + r2.commissionMinor()).isZero();
        assertThat(a1.sellerNetMinor() + r1.sellerNetMinor() + r2.sellerNetMinor()).isZero();
    }

    @Test
    void reverse_is_noop_when_no_accruals_exist() {
        when(accrualRepository.findAccrualsByOrderId("order-1")).thenReturn(List.of());

        service.reverse(new ReversePaymentCommand("order-1", "refund-1", 30_000L, true, NOW));

        verify(accrualRepository, never()).appendAll(any());
    }
}
