package com.example.payment.application.service;

import com.example.payment.application.event.PaymentRefundUnresolvedEvent;
import com.example.payment.application.exception.PgConfirmFailedException;
import com.example.payment.application.exception.PgGatewayUnavailableException;
import com.example.payment.application.port.out.PaymentEventPublisher;
import com.example.payment.application.port.out.PaymentGatewayPort;
import com.example.payment.application.port.out.PaymentGatewayStatus;
import com.example.payment.application.port.out.PaymentMetricRecorder;
import com.example.payment.application.port.out.StrandedRefundRepository;
import com.example.payment.domain.model.StrandedRefund;
import com.example.payment.domain.model.StrandedRefundStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("StrandedRefundReconciler 단위 테스트 (TASK-BE-438 AC-2..AC-5)")
class StrandedRefundReconcilerTest {

    private static final Instant NOW = Instant.parse("2026-06-26T10:00:00Z");
    private static final long INITIAL_BACKOFF_MS = 1000L;
    private static final long MAX_BACKOFF_MS = 30000L;

    @Mock
    private StrandedRefundRepository repository;
    @Mock
    private PaymentGatewayPort paymentGateway;
    @Mock
    private PaymentEventPublisher paymentEventPublisher;
    @Mock
    private PaymentMetricRecorder metrics;

    private StrandedRefundReconciler reconciler(int maxAttempts) {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        return new StrandedRefundReconciler(repository, paymentGateway, paymentEventPublisher,
                metrics, clock, maxAttempts, INITIAL_BACKOFF_MS, MAX_BACKOFF_MS);
    }

    private StrandedRefund openRecord(int attempts) {
        return StrandedRefund.reconstitute(
                1L, "pay-1", "order-1", "pk_test_123", 30000L, "PgGatewayUnavailableException",
                StrandedRefundStatus.STRANDED, attempts, NOW, null, NOW, NOW, null);
    }

    @Test
    @DisplayName("AC-2: PG 상태가 이미 CANCELED 면 cancelPayment 를 절대 호출하지 않고 RESOLVED 로 표시한다 (이중환불 방지)")
    void reconcile_pgAlreadyCanceled_resolvesWithoutReCancel() {
        StrandedRefund record = openRecord(0);
        given(repository.findById(1L)).willReturn(Optional.of(record));
        given(paymentGateway.fetchStatus("pk_test_123")).willReturn(PaymentGatewayStatus.CANCELED);

        reconciler(8).reconcile(1L);

        // Never re-issue a cancel — the original transient cancel actually succeeded.
        verify(paymentGateway, never()).cancelPayment(anyString(), anyString());
        verify(metrics).incrementRefundStrandedResolved();
        verify(repository).save(record);
        assertThat(record.getStatus()).isEqualTo(StrandedRefundStatus.RESOLVED);
        assertThat(record.getResolvedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("AC-3: PG 상태가 CAPTURED 면 cancelPayment 를 호출하고, 성공하면 RESOLVED 로 표시한다")
    void reconcile_capturedAndCancelSucceeds_resolves() {
        StrandedRefund record = openRecord(0);
        given(repository.findById(1L)).willReturn(Optional.of(record));
        given(paymentGateway.fetchStatus("pk_test_123")).willReturn(PaymentGatewayStatus.CAPTURED);

        reconciler(8).reconcile(1L);

        verify(paymentGateway).cancelPayment(eq("pk_test_123"), anyString());
        verify(metrics).incrementRefundStrandedResolved();
        verify(paymentEventPublisher, never()).publishPaymentRefundUnresolved(any());
        assertThat(record.getStatus()).isEqualTo(StrandedRefundStatus.RESOLVED);
    }

    @Test
    @DisplayName("AC-4: 일시적 PG 실패 시 attempts 를 올리고 next_attempt_at 을 지수 백오프로 미루며 STRANDED 를 유지한다")
    void reconcile_transientFailure_incrementsAttemptsAndBacksOff() {
        StrandedRefund record = openRecord(0);
        given(repository.findById(1L)).willReturn(Optional.of(record));
        given(paymentGateway.fetchStatus("pk_test_123")).willReturn(PaymentGatewayStatus.CAPTURED);
        doThrow(new PgGatewayUnavailableException("cancel retry exhausted"))
                .when(paymentGateway).cancelPayment(eq("pk_test_123"), anyString());

        reconciler(8).reconcile(1L);

        verify(metrics, never()).incrementRefundStrandedResolved();
        verify(metrics, never()).incrementRefundStrandedUnresolved();
        verify(repository).save(record);
        assertThat(record.getStatus()).isEqualTo(StrandedRefundStatus.STRANDED);
        assertThat(record.getAttempts()).isEqualTo(1);
        // attempt 1 → 1s backoff
        assertThat(record.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofSeconds(1)));
        assertThat(record.getLastError()).contains("cancel transient failure");
    }

    @Test
    @DisplayName("AC-4: fetchStatus 자체가 실패해도 RESOLVED 로 추정하지 않고 일시적 처리(백오프)한다")
    void reconcile_fetchStatusFails_treatedAsTransient() {
        StrandedRefund record = openRecord(2);
        given(repository.findById(1L)).willReturn(Optional.of(record));
        given(paymentGateway.fetchStatus("pk_test_123"))
                .willThrow(new PgGatewayUnavailableException("status fetch exhausted"));

        reconciler(8).reconcile(1L);

        verify(paymentGateway, never()).cancelPayment(anyString(), anyString());
        assertThat(record.getStatus()).isEqualTo(StrandedRefundStatus.STRANDED);
        assertThat(record.getAttempts()).isEqualTo(3);
        // attempt 3 → 1s * 2^2 = 4s backoff
        assertThat(record.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofSeconds(4)));
    }

    @Test
    @DisplayName("AC-5: attempts 가 cap 에 도달하면 terminal UNRESOLVED 로 전이하고 unresolved 메트릭 증가 + 종료 에스컬레이션 이벤트를 발행한다")
    void reconcile_attemptCapExhausted_terminatesUnresolved() {
        // maxAttempts=3, record at attempts=2 → this retry would exhaust (2+1>=3).
        StrandedRefund record = openRecord(2);
        given(repository.findById(1L)).willReturn(Optional.of(record));
        given(paymentGateway.fetchStatus("pk_test_123")).willReturn(PaymentGatewayStatus.CAPTURED);
        doThrow(new PgGatewayUnavailableException("cancel retry exhausted"))
                .when(paymentGateway).cancelPayment(eq("pk_test_123"), anyString());

        reconciler(3).reconcile(1L);

        assertThat(record.getStatus()).isEqualTo(StrandedRefundStatus.UNRESOLVED);
        verify(metrics).incrementRefundStrandedUnresolved();
        verify(metrics, never()).incrementRefundStrandedResolved();

        ArgumentCaptor<PaymentRefundUnresolvedEvent> captor =
                ArgumentCaptor.forClass(PaymentRefundUnresolvedEvent.class);
        verify(paymentEventPublisher).publishPaymentRefundUnresolved(captor.capture());
        PaymentRefundUnresolvedEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo("PaymentRefundUnresolved");
        assertThat(event.payload().paymentId()).isEqualTo("pay-1");
        assertThat(event.payload().orderId()).isEqualTo("order-1");
        assertThat(event.payload().amount()).isEqualTo(30000L);
    }

    @Test
    @DisplayName("AC-5: cancel 이 확정적 4xx(PgConfirmFailedException) 로 거부되면 attempts 와 무관하게 즉시 UNRESOLVED 로 종료한다")
    void reconcile_definitiveRejection_terminatesImmediately() {
        StrandedRefund record = openRecord(0);
        given(repository.findById(1L)).willReturn(Optional.of(record));
        given(paymentGateway.fetchStatus("pk_test_123")).willReturn(PaymentGatewayStatus.CAPTURED);
        doThrow(new PgConfirmFailedException("cancel rejected"))
                .when(paymentGateway).cancelPayment(eq("pk_test_123"), anyString());

        reconciler(8).reconcile(1L);

        assertThat(record.getStatus()).isEqualTo(StrandedRefundStatus.UNRESOLVED);
        verify(metrics).incrementRefundStrandedUnresolved();
        verify(paymentEventPublisher).publishPaymentRefundUnresolved(any());
    }

    @Test
    @DisplayName("AC-5: 이미 terminal(UNRESOLVED) 인 행이 재선택되어도 idempotent — 아무 PG 호출/메트릭 없이 skip")
    void reconcile_alreadyTerminal_isIdempotentNoop() {
        StrandedRefund terminal = StrandedRefund.reconstitute(
                1L, "pay-1", "order-1", "pk_test_123", 30000L, "PgGatewayUnavailableException",
                StrandedRefundStatus.UNRESOLVED, 8, NOW, "cap", NOW, NOW, null);
        given(repository.findById(1L)).willReturn(Optional.of(terminal));

        reconciler(8).reconcile(1L);

        verify(paymentGateway, never()).fetchStatus(anyString());
        verify(paymentGateway, never()).cancelPayment(anyString(), anyString());
        verify(repository, never()).save(any());
        verify(metrics, never()).incrementRefundStrandedResolved();
        verify(metrics, never()).incrementRefundStrandedUnresolved();
    }
}
