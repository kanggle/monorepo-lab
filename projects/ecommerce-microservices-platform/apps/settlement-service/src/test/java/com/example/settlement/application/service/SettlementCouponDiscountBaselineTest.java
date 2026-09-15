package com.example.settlement.application.service;

import com.example.settlement.domain.model.AccrualType;
import com.example.settlement.domain.model.CommissionAccrual;
import com.example.settlement.domain.model.CommissionRate;
import com.example.settlement.domain.model.OrderSnapshot;
import com.example.settlement.domain.model.OrderSnapshotLine;
import com.example.settlement.domain.repository.CommissionAccrualRepository;
import com.example.settlement.domain.repository.OrderSnapshotRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TASK-BE-592 AC-0 — what settlement records TODAY for a coupon-discounted order.
 *
 * <p>This is a <b>characterization</b> test: every assertion states the current numbers, so it is
 * green on the unmodified code and its values are the measurement. The implementation commit
 * replaces it with the decided behaviour (platform bears the discount, recorded as a separate
 * promotion-cost row).
 *
 * <p>Order shape used throughout (TASK-INT-026): line subtotal 30,000, coupon discount 5,000, so the
 * customer paid <b>25,000</b>. The snapshot record has no discount field at all, so the discount
 * cannot even reach this service — the tests below pass it nowhere because there is nowhere to pass it.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("쿠폰 할인 주문의 정산 — 수정 전 기준 측정 (TASK-BE-592 AC-0)")
class SettlementCouponDiscountBaselineTest {

    @Mock
    private OrderSnapshotRepository snapshotRepository;
    @Mock
    private CommissionAccrualRepository accrualRepository;
    @Mock
    private CommissionRateResolver rateResolver;
    @InjectMocks
    private SettlementService service;

    private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");
    private static final long PAID = 25_000L;

    @SuppressWarnings("unchecked")
    private List<CommissionAccrual> appended() {
        ArgumentCaptor<List<CommissionAccrual>> captor = ArgumentCaptor.forClass(List.class);
        verify(accrualRepository).appendAll(captor.capture());
        return captor.getValue();
    }

    @Test
    @DisplayName("단일 셀러: 수수료는 할인 전 30,000 에 매겨지고, 할인 5,000 은 어느 행에도 없다")
    void singleSeller_accrualIgnoresDiscount() {
        when(accrualRepository.existsAccrualFor("order-1", "pay-1")).thenReturn(false);
        when(snapshotRepository.findByOrderId("order-1")).thenReturn(Optional.of(
                new OrderSnapshot("order-1", "tenantA", List.of(new OrderSnapshotLine("seller-1", 30_000L)))));
        when(rateResolver.resolve("tenantA", "seller-1")).thenReturn(CommissionRate.sellerOverride(1000));

        service.accrue(new AccruePaymentCommand("order-1", "pay-1", NOW));

        List<CommissionAccrual> rows = appended();
        assertThat(rows).hasSize(1);
        CommissionAccrual row = rows.get(0);
        assertThat(row.grossMinor()).isEqualTo(30_000L);
        assertThat(row.commissionMinor()).isEqualTo(3_000L);
        assertThat(row.sellerNetMinor()).isEqualTo(27_000L);

        long ledgerGross = rows.stream().mapToLong(CommissionAccrual::grossMinor).sum();
        // The ledger explains 30,000 while 25,000 was captured — the 5,000 gap is recorded nowhere.
        assertThat(ledgerGross - PAID).isEqualTo(5_000L);
        // The seller is owed 27,000 out of 25,000 captured: the platform is 2,000 short, yet shows +3,000.
        assertThat(row.sellerNetMinor() - PAID).isEqualTo(2_000L);
    }

    @Test
    @DisplayName("두 셀러: 셀러 순수익 합 28,000 이 결제액 25,000 을 넘는다")
    void twoSellers_sellerNetExceedsCapturedAmount() {
        when(accrualRepository.existsAccrualFor("order-2", "pay-2")).thenReturn(false);
        when(snapshotRepository.findByOrderId("order-2")).thenReturn(Optional.of(
                new OrderSnapshot("order-2", "tenantA", List.of(
                        new OrderSnapshotLine("seller-1", 20_000L),
                        new OrderSnapshotLine("seller-2", 10_000L)))));
        when(rateResolver.resolve("tenantA", "seller-1")).thenReturn(CommissionRate.sellerOverride(1000));
        when(rateResolver.resolve("tenantA", "seller-2")).thenReturn(CommissionRate.platformDefault(0));

        service.accrue(new AccruePaymentCommand("order-2", "pay-2", NOW));

        List<CommissionAccrual> rows = appended();
        long sellerNet = rows.stream().mapToLong(CommissionAccrual::sellerNetMinor).sum();
        long commission = rows.stream().mapToLong(CommissionAccrual::commissionMinor).sum();
        assertThat(sellerNet).isEqualTo(28_000L);
        assertThat(commission).isEqualTo(2_000L);
        assertThat(sellerNet - PAID).isEqualTo(3_000L);
    }

    @Test
    @DisplayName("부분 환불 10,000: 할인 전 총액 기준 비율로 총액 10,000 만 역분개한다 (결제액 기준이면 12,000)")
    void partialRefund_ratioUsesPreDiscountGross() {
        CommissionAccrual a1 = new CommissionAccrual("a1", "tenantA", "order-1", "pay-1", "seller-1",
                AccrualType.ACCRUAL, 30_000L, 1000, 3_000L, 27_000L, NOW);
        when(accrualRepository.findAccrualsByOrderId("order-1")).thenReturn(List.of(a1));
        when(accrualRepository.findReversalsByOrderId("order-1")).thenReturn(List.of());

        service.reverse(new ReversePaymentCommand("order-1", "refund-1", 10_000L, false, NOW));

        List<CommissionAccrual> first = appended();
        assertThat(first).hasSize(1);
        CommissionAccrual r1 = first.get(0);
        assertThat(r1.grossMinor()).isEqualTo(-10_000L);
        assertThat(r1.commissionMinor()).isEqualTo(-1_000L);
        assertThat(r1.sellerNetMinor()).isEqualTo(-9_000L);
        // Against the 25,000 actually captured, 10,000 is 40% → 12,000 gross. Today it is 1/3 → 10,000.
        assertThat(30_000L * 10_000L / PAID).isEqualTo(12_000L);

        // The final refund of the remaining 15,000 reverses the exact remainder, so the order still nets to 0.
        Mockito.reset(accrualRepository);
        when(accrualRepository.findAccrualsByOrderId("order-1")).thenReturn(List.of(a1));
        when(accrualRepository.findReversalsByOrderId("order-1")).thenReturn(List.of(r1));

        service.reverse(new ReversePaymentCommand("order-1", "refund-2", 15_000L, true, NOW));

        CommissionAccrual r2 = appended().get(0);
        assertThat(r2.grossMinor()).isEqualTo(-20_000L);
        assertThat(r2.commissionMinor()).isEqualTo(-2_000L);
        assertThat(r2.sellerNetMinor()).isEqualTo(-18_000L);
        assertThat(a1.grossMinor() + r1.grossMinor() + r2.grossMinor()).isZero();
        assertThat(a1.sellerNetMinor() + r1.sellerNetMinor() + r2.sellerNetMinor()).isZero();
    }
}
