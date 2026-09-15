package com.example.settlement.application.service;

import com.example.settlement.domain.model.AccrualType;
import com.example.settlement.domain.model.CommissionAccrual;
import com.example.settlement.domain.model.CommissionRate;
import com.example.settlement.domain.model.OrderSnapshot;
import com.example.settlement.domain.model.OrderSnapshotLine;
import com.example.settlement.domain.model.PromotionCost;
import com.example.settlement.domain.model.PromotionCostType;
import com.example.settlement.domain.repository.CommissionAccrualRepository;
import com.example.settlement.domain.repository.OrderSnapshotRepository;
import com.example.settlement.domain.repository.PromotionCostRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-592 — the platform bears the coupon discount, recorded as a separate promotion-cost row.
 *
 * <p>Same order shapes as the AC-0 baseline measurement: line subtotal 30,000, discount 5,000,
 * captured <b>25,000</b>. The invariant every case checks, per order:
 * {@code Σ commission_accrual.gross − Σ promotion_cost.amount = captured}.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("쿠폰 할인 주문의 정산 — 플랫폼 부담 · 별도 프로모션 비용 행 (TASK-BE-592)")
class SettlementCouponDiscountTest {

    @Mock
    private OrderSnapshotRepository snapshotRepository;
    @Mock
    private CommissionAccrualRepository accrualRepository;
    @Mock
    private CommissionRateResolver rateResolver;
    @Mock
    private PromotionCostRepository promotionCostRepository;
    @InjectMocks
    private SettlementService service;

    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");

    @SuppressWarnings("unchecked")
    private List<CommissionAccrual> appendedAccruals() {
        ArgumentCaptor<List<CommissionAccrual>> captor = ArgumentCaptor.forClass(List.class);
        verify(accrualRepository).appendAll(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private List<PromotionCost> appendedCosts() {
        ArgumentCaptor<List<PromotionCost>> captor = ArgumentCaptor.forClass(List.class);
        verify(promotionCostRepository).appendAll(captor.capture());
        return captor.getValue();
    }

    private static long gross(List<CommissionAccrual> rows) {
        return rows.stream().mapToLong(CommissionAccrual::grossMinor).sum();
    }

    private static long amount(List<PromotionCost> rows) {
        return rows.stream().mapToLong(PromotionCost::amountMinor).sum();
    }

    // ── accrue ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("단일 셀러: 수수료·셀러 순수익은 할인 전 그대로, 할인 5,000 은 COST 행 하나로 따로 적힌다")
    void accrue_singleSeller_commissionUnchanged_discountBookedSeparately() {
        when(accrualRepository.existsAccrualFor("order-1", "pay-1")).thenReturn(false);
        when(snapshotRepository.findByOrderId("order-1")).thenReturn(Optional.of(new OrderSnapshot(
                "order-1", "tenantA", List.of(new OrderSnapshotLine("seller-1", 30_000L)), 5_000L, "coupon-1")));
        when(rateResolver.resolve("tenantA", "seller-1")).thenReturn(CommissionRate.sellerOverride(1000));

        service.accrue(new AccruePaymentCommand("order-1", "pay-1", NOW));

        List<CommissionAccrual> accruals = appendedAccruals();
        assertThat(accruals).singleElement().satisfies(a -> {
            assertThat(a.grossMinor()).isEqualTo(30_000L);
            assertThat(a.commissionMinor()).isEqualTo(3_000L);
            assertThat(a.sellerNetMinor()).isEqualTo(27_000L);
        });
        List<PromotionCost> costs = appendedCosts();
        assertThat(costs).singleElement().satisfies(c -> {
            assertThat(c.type()).isEqualTo(PromotionCostType.COST);
            assertThat(c.amountMinor()).isEqualTo(5_000L);
            assertThat(c.couponId()).isEqualTo("coupon-1");
            assertThat(c.tenantId()).isEqualTo("tenantA");
            assertThat(c.orderId()).isEqualTo("order-1");
            assertThat(c.paymentId()).isEqualTo("pay-1");
        });
        assertThat(gross(accruals) - amount(costs)).isEqualTo(25_000L);
    }

    @Test
    @DisplayName("두 셀러: 셀러별 순수익은 할인 전 그대로이고 할인은 셀러에게 나뉘지 않는다")
    void accrue_twoSellers_sellerNetUnchanged_costNotSplit() {
        when(accrualRepository.existsAccrualFor("order-2", "pay-2")).thenReturn(false);
        when(snapshotRepository.findByOrderId("order-2")).thenReturn(Optional.of(new OrderSnapshot(
                "order-2", "tenantA", List.of(
                        new OrderSnapshotLine("seller-1", 20_000L),
                        new OrderSnapshotLine("seller-2", 10_000L)), 5_000L, "coupon-1")));
        when(rateResolver.resolve("tenantA", "seller-1")).thenReturn(CommissionRate.sellerOverride(1000));
        when(rateResolver.resolve("tenantA", "seller-2")).thenReturn(CommissionRate.platformDefault(0));

        service.accrue(new AccruePaymentCommand("order-2", "pay-2", NOW));

        List<CommissionAccrual> accruals = appendedAccruals();
        assertThat(accruals).filteredOn(a -> a.sellerId().equals("seller-1")).singleElement()
                .satisfies(a -> assertThat(a.sellerNetMinor()).isEqualTo(18_000L));
        assertThat(accruals).filteredOn(a -> a.sellerId().equals("seller-2")).singleElement()
                .satisfies(a -> assertThat(a.sellerNetMinor()).isEqualTo(10_000L));
        List<PromotionCost> costs = appendedCosts();
        assertThat(costs).singleElement().satisfies(c -> assertThat(c.amountMinor()).isEqualTo(5_000L));
        assertThat(gross(accruals) - amount(costs)).isEqualTo(25_000L);
    }

    @Test
    @DisplayName("쿠폰 없는 주문은 프로모션 비용 행을 쓰지 않는다")
    void accrue_noDiscount_writesNoPromotionCost() {
        when(accrualRepository.existsAccrualFor("order-3", "pay-3")).thenReturn(false);
        when(snapshotRepository.findByOrderId("order-3")).thenReturn(Optional.of(new OrderSnapshot(
                "order-3", "tenantA", List.of(new OrderSnapshotLine("seller-1", 30_000L)))));
        when(rateResolver.resolve("tenantA", "seller-1")).thenReturn(CommissionRate.sellerOverride(1000));

        service.accrue(new AccruePaymentCommand("order-3", "pay-3", NOW));

        assertThat(appendedAccruals()).singleElement()
                .satisfies(a -> assertThat(a.commissionMinor()).isEqualTo(3_000L));
        verifyNoInteractions(promotionCostRepository);
    }

    @Test
    @DisplayName("같은 (주문, 결제) 재전달은 수수료도 프로모션 비용도 다시 적지 않는다")
    void accrue_replay_writesNothing() {
        when(accrualRepository.existsAccrualFor("order-1", "pay-1")).thenReturn(true);

        service.accrue(new AccruePaymentCommand("order-1", "pay-1", NOW));

        verify(accrualRepository, never()).appendAll(any());
        verifyNoInteractions(promotionCostRepository);
    }

    // ── reverse ────────────────────────────────────────────────────────────

    @Test
    @DisplayName("🔴 부분 환불 10,000: 분모는 결제액 25,000 — 총액 12,000 과 프로모션 비용 2,000 을 되돌린다 (12,000 − 2,000 = 환불액)")
    void reverse_partial_usesCapturedDenominator_andReversesCostSameRatio() {
        CommissionAccrual a1 = new CommissionAccrual("a1", "tenantA", "order-1", "pay-1", "seller-1",
                AccrualType.ACCRUAL, 30_000L, 1000, 3_000L, 27_000L, NOW);
        PromotionCost c1 = new PromotionCost("c1", "tenantA", "order-1", "pay-1", "coupon-1",
                PromotionCostType.COST, 5_000L, null, NOW);
        when(accrualRepository.findAccrualsByOrderId("order-1")).thenReturn(List.of(a1));
        when(accrualRepository.findReversalsByOrderId("order-1")).thenReturn(List.of());
        when(snapshotRepository.findByOrderId("order-1")).thenReturn(Optional.of(new OrderSnapshot(
                "order-1", "tenantA", List.of(new OrderSnapshotLine("seller-1", 30_000L)), 5_000L, "coupon-1")));
        when(promotionCostRepository.findByOrderId("order-1")).thenReturn(List.of(c1));

        service.reverse(new ReversePaymentCommand("order-1", "refund-1", 10_000L, false, NOW));

        CommissionAccrual r1 = appendedAccruals().get(0);
        assertThat(r1.grossMinor()).isEqualTo(-12_000L);
        assertThat(r1.commissionMinor()).isEqualTo(-1_200L);
        assertThat(r1.sellerNetMinor()).isEqualTo(-10_800L);
        PromotionCost cr1 = appendedCosts().get(0);
        assertThat(cr1.type()).isEqualTo(PromotionCostType.REVERSAL);
        assertThat(cr1.amountMinor()).isEqualTo(-2_000L);
        assertThat(cr1.reversesCostId()).isEqualTo("c1");
        assertThat(cr1.paymentId()).isEqualTo("refund-1");
        // The money given back: reversed gross minus reversed promotion cost = the refund.
        assertThat(-r1.grossMinor() - (-cr1.amountMinor())).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("부분 → 완전 환불 뒤 두 원장 모두 정확히 0 이 된다")
    void reverse_partialThenFull_bothLedgersNetExactlyZero() {
        CommissionAccrual a1 = new CommissionAccrual("a1", "tenantA", "order-1", "pay-1", "seller-1",
                AccrualType.ACCRUAL, 30_000L, 1000, 3_000L, 27_000L, NOW);
        PromotionCost c1 = new PromotionCost("c1", "tenantA", "order-1", "pay-1", "coupon-1",
                PromotionCostType.COST, 5_000L, null, NOW);
        when(snapshotRepository.findByOrderId("order-1")).thenReturn(Optional.of(new OrderSnapshot(
                "order-1", "tenantA", List.of(new OrderSnapshotLine("seller-1", 30_000L)), 5_000L, "coupon-1")));
        when(accrualRepository.findAccrualsByOrderId("order-1")).thenReturn(List.of(a1));
        when(accrualRepository.findReversalsByOrderId("order-1")).thenReturn(List.of());
        when(promotionCostRepository.findByOrderId("order-1")).thenReturn(List.of(c1));

        service.reverse(new ReversePaymentCommand("order-1", "refund-1", 10_000L, false, NOW));
        CommissionAccrual r1 = appendedAccruals().get(0);
        PromotionCost cr1 = appendedCosts().get(0);

        Mockito.reset(accrualRepository, promotionCostRepository);
        when(accrualRepository.findAccrualsByOrderId("order-1")).thenReturn(List.of(a1));
        when(accrualRepository.findReversalsByOrderId("order-1")).thenReturn(List.of(r1));
        when(promotionCostRepository.findByOrderId("order-1")).thenReturn(List.of(c1, cr1));

        service.reverse(new ReversePaymentCommand("order-1", "refund-2", 15_000L, true, NOW));
        CommissionAccrual r2 = appendedAccruals().get(0);
        PromotionCost cr2 = appendedCosts().get(0);

        assertThat(r2.grossMinor()).isEqualTo(-18_000L);
        assertThat(r2.commissionMinor()).isEqualTo(-1_800L);
        assertThat(r2.sellerNetMinor()).isEqualTo(-16_200L);
        assertThat(cr2.amountMinor()).isEqualTo(-3_000L);
        assertThat(a1.grossMinor() + r1.grossMinor() + r2.grossMinor()).isZero();
        assertThat(a1.commissionMinor() + r1.commissionMinor() + r2.commissionMinor()).isZero();
        assertThat(a1.sellerNetMinor() + r1.sellerNetMinor() + r2.sellerNetMinor()).isZero();
        assertThat(c1.amountMinor() + cr1.amountMinor() + cr2.amountMinor()).isZero();
    }

    @Test
    @DisplayName("두 셀러 부분 환불: 셀러마다 결제액 기준 비율, 프로모션 비용은 주문 단위 한 행")
    void reverse_twoSellers_partial() {
        CommissionAccrual a1 = new CommissionAccrual("a1", "tenantA", "order-2", "pay-2", "seller-1",
                AccrualType.ACCRUAL, 20_000L, 1000, 2_000L, 18_000L, NOW);
        CommissionAccrual a2 = new CommissionAccrual("a2", "tenantA", "order-2", "pay-2", "seller-2",
                AccrualType.ACCRUAL, 10_000L, 0, 0L, 10_000L, NOW);
        PromotionCost c1 = new PromotionCost("c1", "tenantA", "order-2", "pay-2", "coupon-1",
                PromotionCostType.COST, 5_000L, null, NOW);
        when(accrualRepository.findAccrualsByOrderId("order-2")).thenReturn(List.of(a1, a2));
        when(accrualRepository.findReversalsByOrderId("order-2")).thenReturn(List.of());
        when(snapshotRepository.findByOrderId("order-2")).thenReturn(Optional.of(new OrderSnapshot(
                "order-2", "tenantA", List.of(
                        new OrderSnapshotLine("seller-1", 20_000L),
                        new OrderSnapshotLine("seller-2", 10_000L)), 5_000L, "coupon-1")));
        when(promotionCostRepository.findByOrderId("order-2")).thenReturn(List.of(c1));

        service.reverse(new ReversePaymentCommand("order-2", "refund-1", 10_000L, false, NOW));

        List<CommissionAccrual> reversals = appendedAccruals();
        assertThat(reversals).filteredOn(r -> "a1".equals(r.reversesAccrualId())).singleElement().satisfies(r -> {
            assertThat(r.grossMinor()).isEqualTo(-8_000L);
            assertThat(r.commissionMinor()).isEqualTo(-800L);
            assertThat(r.sellerNetMinor()).isEqualTo(-7_200L);
        });
        assertThat(reversals).filteredOn(r -> "a2".equals(r.reversesAccrualId())).singleElement().satisfies(r -> {
            assertThat(r.grossMinor()).isEqualTo(-4_000L);
            assertThat(r.commissionMinor()).isZero();
            assertThat(r.sellerNetMinor()).isEqualTo(-4_000L);
        });
        List<PromotionCost> costs = appendedCosts();
        assertThat(costs).singleElement().satisfies(c -> assertThat(c.amountMinor()).isEqualTo(-2_000L));
        assertThat(-gross(reversals) + amount(costs)).isEqualTo(10_000L);
    }

    @Test
    @DisplayName("반올림 잔차가 있는 주문도 완전 환불 뒤 두 원장이 정확히 0 이다")
    void reverse_roundingResidue_netsExactlyZero() {
        // gross 30,001 @1500bps → commission round(4,500.15) = 4,500, net 25,501; discount 5,000 → captured 25,001.
        CommissionAccrual a1 = new CommissionAccrual("a1", "tenantA", "order-4", "pay-4", "seller-1",
                AccrualType.ACCRUAL, 30_001L, 1500, 4_500L, 25_501L, NOW);
        PromotionCost c1 = new PromotionCost("c1", "tenantA", "order-4", "pay-4", "coupon-1",
                PromotionCostType.COST, 5_000L, null, NOW);
        when(snapshotRepository.findByOrderId("order-4")).thenReturn(Optional.of(new OrderSnapshot(
                "order-4", "tenantA", List.of(new OrderSnapshotLine("seller-1", 30_001L)), 5_000L, "coupon-1")));
        when(accrualRepository.findAccrualsByOrderId("order-4")).thenReturn(List.of(a1));
        when(accrualRepository.findReversalsByOrderId("order-4")).thenReturn(List.of());
        when(promotionCostRepository.findByOrderId("order-4")).thenReturn(List.of(c1));

        // partial 10,000: gross round(30,001 × 10,000 / 25,001) = round(12,000.08) = 12,000 → (1,800, 10,200);
        // cost round(5,000 × 10,000 / 25,001) = round(1,999.92) = 2,000.
        service.reverse(new ReversePaymentCommand("order-4", "refund-1", 10_000L, false, NOW));
        CommissionAccrual r1 = appendedAccruals().get(0);
        PromotionCost cr1 = appendedCosts().get(0);
        assertThat(r1.grossMinor()).isEqualTo(-12_000L);
        assertThat(r1.commissionMinor()).isEqualTo(-1_800L);
        assertThat(cr1.amountMinor()).isEqualTo(-2_000L);

        Mockito.reset(accrualRepository, promotionCostRepository);
        when(accrualRepository.findAccrualsByOrderId("order-4")).thenReturn(List.of(a1));
        when(accrualRepository.findReversalsByOrderId("order-4")).thenReturn(List.of(r1));
        when(promotionCostRepository.findByOrderId("order-4")).thenReturn(List.of(c1, cr1));

        service.reverse(new ReversePaymentCommand("order-4", "refund-2", 15_001L, true, NOW));
        CommissionAccrual r2 = appendedAccruals().get(0);
        PromotionCost cr2 = appendedCosts().get(0);

        assertThat(r2.grossMinor()).isEqualTo(-18_001L);
        assertThat(r2.commissionMinor()).isEqualTo(-2_700L);
        assertThat(r2.sellerNetMinor()).isEqualTo(-15_301L);
        assertThat(cr2.amountMinor()).isEqualTo(-3_000L);
        assertThat(a1.grossMinor() + r1.grossMinor() + r2.grossMinor()).isZero();
        assertThat(a1.commissionMinor() + r1.commissionMinor() + r2.commissionMinor()).isZero();
        assertThat(a1.sellerNetMinor() + r1.sellerNetMinor() + r2.sellerNetMinor()).isZero();
        assertThat(c1.amountMinor() + cr1.amountMinor() + cr2.amountMinor()).isZero();
    }

    @Test
    @DisplayName("쿠폰 없는 주문의 환불은 변경 전과 같다 — 분모 30,000, 프로모션 원장은 건드리지 않는다")
    void reverse_noDiscount_sameAsBefore() {
        CommissionAccrual a1 = new CommissionAccrual("a1", "tenantA", "order-3", "pay-3", "seller-1",
                AccrualType.ACCRUAL, 30_000L, 1000, 3_000L, 27_000L, NOW);
        when(accrualRepository.findAccrualsByOrderId("order-3")).thenReturn(List.of(a1));
        when(accrualRepository.findReversalsByOrderId("order-3")).thenReturn(List.of());
        when(snapshotRepository.findByOrderId("order-3")).thenReturn(Optional.of(new OrderSnapshot(
                "order-3", "tenantA", List.of(new OrderSnapshotLine("seller-1", 30_000L)))));

        service.reverse(new ReversePaymentCommand("order-3", "refund-1", 10_000L, false, NOW));

        CommissionAccrual r1 = appendedAccruals().get(0);
        assertThat(r1.grossMinor()).isEqualTo(-10_000L);
        assertThat(r1.commissionMinor()).isEqualTo(-1_000L);
        assertThat(r1.sellerNetMinor()).isEqualTo(-9_000L);
        verifyNoInteractions(promotionCostRepository);
    }
}
