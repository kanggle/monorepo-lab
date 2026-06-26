package com.example.payment.application.service;

import com.example.payment.application.port.out.PaymentMetricRecorder;
import com.example.payment.application.port.out.StrandedRefundRepository;
import com.example.payment.domain.model.StrandedRefund;
import com.example.payment.domain.model.StrandedRefundStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("StrandedRefundSweeper 단위 테스트 (TASK-BE-438 AC-6)")
class StrandedRefundSweeperTest {

    private static final Instant NOW = Instant.parse("2026-06-26T10:00:00Z");

    @Mock
    private StrandedRefundRepository repository;
    @Mock
    private StrandedRefundReconciler reconciler;
    @Mock
    private PaymentMetricRecorder metrics;

    private StrandedRefundSweeper sweeper() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new StrandedRefundSweeper(repository, reconciler, metrics, clock, 50);
    }

    private StrandedRefund record(long id) {
        return StrandedRefund.reconstitute(
                id, "pay-" + id, "order-" + id, "pk_" + id, 30000L, "PgGatewayUnavailableException",
                StrandedRefundStatus.STRANDED, 0, NOW, null, NOW, NOW, null);
    }

    @Test
    @DisplayName("생성 시 payment_refund_stranded_open 게이지 supplier 를 등록한다")
    void constructor_registersOpenGauge() {
        sweeper();
        verify(metrics).registerStrandedOpenGauge(any());
    }

    @Test
    @DisplayName("AC-6: 한 행(A)의 reconcile 이 던져도 나머지 행(B)은 계속 처리된다 (per-record REQUIRES_NEW 경계 — 배치 롤백 없음)")
    void sweep_poisonedRecordDoesNotBlockBatch() {
        given(repository.findDue(eq(NOW), anyInt())).willReturn(List.of(record(1L), record(2L)));
        doThrow(new RuntimeException("record 1 poisoned")).when(reconciler).reconcile(1L);

        sweeper().sweep();

        // Both records are dispatched — A's failure is isolated, B still processes.
        verify(reconciler).reconcile(1L);
        verify(reconciler).reconcile(2L);
    }

    @Test
    @DisplayName("due 행이 없으면 reconciler 를 호출하지 않는다")
    void sweep_noDueRecords_doesNothing() {
        given(repository.findDue(eq(NOW), anyInt())).willReturn(List.of());

        sweeper().sweep();

        verify(reconciler, never()).reconcile(any());
    }

    @Test
    @DisplayName("findDue 가 예외를 던지면 tick 은 조용히 종료한다 (다음 tick 재시도)")
    void sweep_findDueThrows_swallowsAndReturns() {
        given(repository.findDue(eq(NOW), anyInt())).willThrow(new RuntimeException("db down"));

        sweeper().sweep();

        verify(reconciler, never()).reconcile(any());
    }
}
