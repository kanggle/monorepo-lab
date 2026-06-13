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

    @Test
    void reverse_negates_each_accrual_to_net_zero() {
        when(accrualRepository.existsReversalFor("order-1", "refund-1")).thenReturn(false);
        when(accrualRepository.findAccrualsByOrderId("order-1")).thenReturn(List.of(
                new CommissionAccrual("a1", "tenantA", "order-1", "pay-1", "seller-1",
                        AccrualType.ACCRUAL, 30_000L, 1000, 3_000L, 27_000L, NOW)));

        service.reverse(new ReversePaymentCommand("order-1", "refund-1", NOW));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<CommissionAccrual>> captor = ArgumentCaptor.forClass(List.class);
        verify(accrualRepository).appendAll(captor.capture());
        List<CommissionAccrual> reversals = captor.getValue();
        assertThat(reversals).hasSize(1);
        CommissionAccrual rev = reversals.get(0);
        assertThat(rev.type()).isEqualTo(AccrualType.REVERSAL);
        assertThat(rev.commissionMinor()).isEqualTo(-3_000L);
        assertThat(rev.sellerNetMinor()).isEqualTo(-27_000L);
        assertThat(rev.paymentId()).isEqualTo("refund-1");
    }

    @Test
    void reverse_is_idempotent_on_order_refund_key() {
        when(accrualRepository.existsReversalFor("order-1", "refund-1")).thenReturn(true);

        service.reverse(new ReversePaymentCommand("order-1", "refund-1", NOW));

        verify(accrualRepository, never()).findAccrualsByOrderId(anyString());
        verify(accrualRepository, never()).appendAll(any());
    }

    @Test
    void reverse_is_noop_when_no_accruals_exist() {
        when(accrualRepository.existsReversalFor("order-1", "refund-1")).thenReturn(false);
        when(accrualRepository.findAccrualsByOrderId("order-1")).thenReturn(List.of());

        service.reverse(new ReversePaymentCommand("order-1", "refund-1", NOW));

        verify(accrualRepository, never()).appendAll(any());
    }
}
