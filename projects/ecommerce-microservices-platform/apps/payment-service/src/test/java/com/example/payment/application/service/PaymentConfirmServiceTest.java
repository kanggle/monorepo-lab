package com.example.payment.application.service;

import com.example.payment.application.event.PaymentCompletedEvent;
import com.example.payment.application.exception.AmountMismatchException;
import com.example.payment.application.exception.PaymentAlreadyCompletedException;
import com.example.payment.application.exception.PgConfirmFailedException;
import com.example.payment.application.exception.PgGatewayUnavailableException;
import com.example.payment.application.exception.UnauthorizedPaymentAccessException;
import com.example.payment.application.port.out.PaymentEventPublisher;
import com.example.payment.application.port.out.PaymentGatewayConfirmResult;
import com.example.payment.application.port.out.PaymentGatewayPort;
import com.example.payment.application.port.out.PaymentMetricRecorder;
import com.example.payment.application.port.out.PaymentRepository;
import com.example.payment.domain.exception.PaymentNotFoundException;
import com.example.payment.domain.model.Payment;
import com.example.payment.domain.model.PaymentStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentConfirmService 단위 테스트")
class PaymentConfirmServiceTest {

    private PaymentConfirmService paymentConfirmService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentGatewayPort paymentGateway;

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    @Mock
    private PaymentMetricRecorder paymentMetricRecorder;

    @BeforeEach
    void setUp() {
        paymentConfirmService = new PaymentConfirmService(
                paymentRepository, paymentGateway, paymentEventPublisher, paymentMetricRecorder
        );
    }

    @Test
    @DisplayName("정상 confirm 시 PG 승인 후 COMPLETED 상태로 저장되고 이벤트가 발행된다")
    void confirm_happyPath_savesCompletedPaymentAndPublishesEvent() {
        Payment payment = Payment.create("order-1", "user-1", 30000L);
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));
        given(paymentGateway.confirmPayment("pk_test_123", "order-1", 30000L))
                .willReturn(new PaymentGatewayConfirmResult("CARD", "https://receipt.url"));
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        PaymentConfirmResult result = paymentConfirmService.confirm("user-1", "pk_test_123", "order-1", 30000L);

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.paymentMethod()).isEqualTo("CARD");
        assertThat(result.receiptUrl()).isEqualTo("https://receipt.url");
        assertThat(result.paidAt()).isNotNull();

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.COMPLETED);
        assertThat(captor.getValue().getPaymentKey()).isEqualTo("pk_test_123");

        verify(paymentMetricRecorder).incrementPaymentCompleted();
        verify(paymentMetricRecorder).addPaymentAmount(30000L);
        verify(paymentEventPublisher).publishPaymentCompleted(any(PaymentCompletedEvent.class));
    }

    @Test
    @DisplayName("존재하지 않는 orderId로 confirm 시 PaymentNotFoundException이 발생한다")
    void confirm_paymentNotFound_throwsException() {
        given(paymentRepository.findByOrderId("order-x")).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentConfirmService.confirm("user-1", "pk_test_123", "order-x", 30000L))
                .isInstanceOf(PaymentNotFoundException.class);
    }

    @Test
    @DisplayName("다른 사용자가 confirm 시 UnauthorizedPaymentAccessException이 발생한다")
    void confirm_differentUser_throwsUnauthorized() {
        Payment payment = Payment.create("order-1", "user-1", 30000L);
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentConfirmService.confirm("attacker", "pk_test_123", "order-1", 30000L))
                .isInstanceOf(UnauthorizedPaymentAccessException.class);
    }

    @Test
    @DisplayName("이미 COMPLETED 상태인 결제에 confirm 시 PaymentAlreadyCompletedException이 발생한다")
    void confirm_alreadyCompleted_throwsConflict() {
        Payment payment = Payment.create("order-1", "user-1", 30000L);
        payment.confirm("pk_existing", "CARD", null);
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentConfirmService.confirm("user-1", "pk_test_123", "order-1", 30000L))
                .isInstanceOf(PaymentAlreadyCompletedException.class);
    }

    @Test
    @DisplayName("금액 불일치 시 AmountMismatchException이 발생한다")
    void confirm_amountMismatch_throwsBadRequest() {
        Payment payment = Payment.create("order-1", "user-1", 30000L);
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentConfirmService.confirm("user-1", "pk_test_123", "order-1", 50000L))
                .isInstanceOf(AmountMismatchException.class);
    }

    @Test
    @DisplayName("PG 승인 실패 시 결제가 FAILED 상태로 전이되고 PgConfirmFailedException이 발생한다")
    void confirm_pgFailure_setsFailedAndThrows() {
        Payment payment = Payment.create("order-1", "user-1", 30000L);
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));
        given(paymentGateway.confirmPayment("pk_test_123", "order-1", 30000L))
                .willThrow(new PgConfirmFailedException("server error"));
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        assertThatThrownBy(() -> paymentConfirmService.confirm("user-1", "pk_test_123", "order-1", 30000L))
                .isInstanceOf(PgConfirmFailedException.class);

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.FAILED);

        verify(paymentMetricRecorder, never()).incrementPaymentCompleted();
        verify(paymentEventPublisher, never()).publishPaymentCompleted(any());
    }

    @Test
    @DisplayName("PgGatewayUnavailableException 발생 시 결제 상태를 변경하지 않고 그대로 propagate 한다 (ADR-MONO-005 § D4 — 전송 실패는 PG 상태 불명, idempotent retry 허용)")
    void confirm_pgGatewayUnavailable_doesNotChangeStateAndPropagates() {
        Payment payment = Payment.create("order-1", "user-1", 30000L);
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));
        given(paymentGateway.confirmPayment("pk_test_123", "order-1", 30000L))
                .willThrow(new PgGatewayUnavailableException("retry exhausted"));

        assertThatThrownBy(() -> paymentConfirmService.confirm("user-1", "pk_test_123", "order-1", 30000L))
                .isInstanceOf(PgGatewayUnavailableException.class);

        // CRITICAL: row stays in PENDING. No save() called — caller (HTTP user)
        // can idempotently retry without being locked out by FAILED status.
        verify(paymentRepository, never()).save(any());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PENDING);

        verify(paymentMetricRecorder, never()).incrementPaymentCompleted();
        verify(paymentEventPublisher, never()).publishPaymentCompleted(any());
    }

    // ── TASK-BE-435: money-safe confirm-vs-cancel interleave ─────────────────

    @Test
    @DisplayName("AC-3 pre-capture guard: 주문 취소로 VOIDED 된 결제 confirm 시 PG 호출 없이 거부된다 (자금 미캡처)")
    void confirm_voidedBeforeCapture_rejectedWithoutPgCall() {
        Payment payment = Payment.reconstitute(
                "pay-1", "order-1", "user-1", "ecommerce", 30000L, 0L,
                PaymentStatus.VOIDED, java.time.LocalDateTime.now(), null, null, null, null, null);
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentConfirmService.confirm("user-1", "pk_test_123", "order-1", 30000L))
                .isInstanceOf(PaymentAlreadyCompletedException.class);

        // Never hit the PG, never captured, never published.
        verify(paymentGateway, never()).confirmPayment(any(), any(), org.mockito.ArgumentMatchers.anyLong());
        verify(paymentRepository, never()).save(any());
        verify(paymentEventPublisher, never()).publishPaymentCompleted(any());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.VOIDED);
    }

    @Test
    @DisplayName("AC-3 post-capture guard: confirm 중 OrderCancelled 가 VOIDED 를 커밋하면 캡처된 금액을 즉시 PG cancel 하고 COMPLETED 로 진행하지 않는다 (자금 미보유)")
    void confirm_voidedDuringCapture_autoCancelsCapturedAmount() {
        // Initial read: PENDING (pre-capture guard passes). Post-capture re-read: VOIDED
        // (OrderCancelled committed during the slow PG call).
        Payment pending = Payment.create("order-1", "user-1", 30000L);
        Payment voided = Payment.reconstitute(
                "pay-1", "order-1", "user-1", "ecommerce", 30000L, 0L,
                PaymentStatus.VOIDED, java.time.LocalDateTime.now(), null, null, null, null, null);
        given(paymentRepository.findByOrderId("order-1"))
                .willReturn(Optional.of(pending))   // initial read
                .willReturn(Optional.of(voided));   // post-capture re-read
        given(paymentGateway.confirmPayment("pk_test_123", "order-1", 30000L))
                .willReturn(new PaymentGatewayConfirmResult("CARD", "https://receipt.url"));

        assertThatThrownBy(() -> paymentConfirmService.confirm("user-1", "pk_test_123", "order-1", 30000L))
                .isInstanceOf(PaymentAlreadyCompletedException.class);

        // Captured at PG, then immediately auto-cancelled (full cancel) — funds not retained.
        verify(paymentGateway).confirmPayment("pk_test_123", "order-1", 30000L);
        verify(paymentGateway).cancelPayment("pk_test_123", "Order cancelled during confirm");
        // Did NOT advance to COMPLETED nor publish PaymentCompleted.
        verify(paymentRepository, never()).save(any());
        verify(paymentEventPublisher, never()).publishPaymentCompleted(any());
        verify(paymentMetricRecorder, never()).incrementPaymentCompleted();
    }
}
