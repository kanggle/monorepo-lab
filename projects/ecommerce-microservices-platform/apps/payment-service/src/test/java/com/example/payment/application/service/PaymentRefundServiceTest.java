package com.example.payment.application.service;

import com.example.payment.application.event.PaymentRefundedEvent;
import com.example.payment.application.exception.PgGatewayUnavailableException;
import com.example.payment.application.exception.UnauthorizedPaymentAccessException;
import com.example.payment.domain.exception.PaymentNotFoundException;
import com.example.payment.application.port.out.PaymentEventPublisher;
import com.example.payment.application.port.out.PaymentGatewayPort;
import com.example.payment.application.port.out.PaymentMetricRecorder;
import com.example.payment.domain.model.Payment;
import com.example.payment.domain.model.PaymentStatus;
import com.example.payment.application.port.out.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentRefundService 단위 테스트")
class PaymentRefundServiceTest {

    private PaymentRefundService paymentRefundService;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    @Mock
    private PaymentMetricRecorder paymentMetricRecorder;

    @Mock
    private PaymentGatewayPort paymentGateway;

    @BeforeEach
    void setUp() {
        paymentRefundService = new PaymentRefundService(
                paymentRepository, paymentEventPublisher, paymentMetricRecorder, paymentGateway
        );
    }

    private Payment completedPaymentWithPgKey() {
        return Payment.reconstitute(
                "pay-1", "order-1", "user-1", "ecommerce", 30000L, 0L,
                PaymentStatus.COMPLETED,
                LocalDateTime.now(), LocalDateTime.now(), null,
                "pk_test_123", "CARD", null
        );
    }

    private Payment completedPaymentWithoutPgKey() {
        return Payment.reconstitute(
                "pay-1", "order-1", "user-1", "ecommerce", 30000L, 0L,
                PaymentStatus.COMPLETED,
                LocalDateTime.now(), LocalDateTime.now(), null,
                null, null, null
        );
    }

    private Payment pendingPayment() {
        return Payment.reconstitute(
                "pay-1", "order-1", "user-1", "ecommerce", 30000L, 0L,
                PaymentStatus.PENDING,
                LocalDateTime.now(), null, null,
                null, null, null
        );
    }

    // ── TASK-BE-435: handleOrderCancelled branch (COMPLETED→refund / PENDING→void / terminal→no-op) ──

    @Test
    @DisplayName("COMPLETED 결제에 OrderCancelled → 전액 환불 (refund 경로), PG cancel 호출")
    void handleOrderCancelled_completed_refunds() {
        Payment payment = completedPaymentWithPgKey();
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        paymentRefundService.handleOrderCancelled("order-1");

        verify(paymentGateway).cancelPayment("pk_test_123", "Order cancelled");
        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentEventPublisher).publishPaymentRefunded(any());
    }

    @Test
    @DisplayName("PENDING 결제에 OrderCancelled → VOIDED 전이 (환불/PG cancel/이벤트 없음)")
    void handleOrderCancelled_pending_voids() {
        Payment payment = pendingPayment();
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        paymentRefundService.handleOrderCancelled("order-1");

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.VOIDED);
        // No money movement: void never touches the PG and emits no refund event.
        verify(paymentGateway, never()).cancelPayment(any(), any());
        verify(paymentEventPublisher, never()).publishPaymentRefunded(any());
    }

    @Test
    @DisplayName("이미 VOIDED 결제에 중복 OrderCancelled → 멱등 no-op (저장/PG/이벤트 없음)")
    void handleOrderCancelled_alreadyVoided_isNoOp() {
        Payment payment = pendingPayment();
        payment.voidForOrderCancelled();
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));

        paymentRefundService.handleOrderCancelled("order-1");

        verify(paymentRepository, never()).save(any());
        verify(paymentGateway, never()).cancelPayment(any(), any());
        verify(paymentEventPublisher, never()).publishPaymentRefunded(any());
    }

    @Test
    @DisplayName("이미 REFUNDED 결제에 중복 OrderCancelled → 멱등 no-op (이중 환불 없음)")
    void handleOrderCancelled_alreadyRefunded_isNoOp() {
        Payment payment = completedPaymentWithPgKey();
        payment.refund();
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));

        paymentRefundService.handleOrderCancelled("order-1");

        verify(paymentRepository, never()).save(any());
        verify(paymentGateway, never()).cancelPayment(any(), any());
        verify(paymentEventPublisher, never()).publishPaymentRefunded(any());
    }

    @Test
    @DisplayName("Payment가 없는 orderId의 OrderCancelled → no-op")
    void handleOrderCancelled_noPayment_isNoOp() {
        given(paymentRepository.findByOrderId("order-x")).willReturn(Optional.empty());

        paymentRefundService.handleOrderCancelled("order-x");

        verify(paymentRepository, never()).save(any());
        verify(paymentEventPublisher, never()).publishPaymentRefunded(any());
    }

    @Test
    @DisplayName("PARTIALLY_REFUNDED 결제에 OrderCancelled → 잔여액 환불 (refund 경로)")
    void handleOrderCancelled_partiallyRefunded_refundsRemainder() {
        Payment payment = completedPaymentWithPgKey();
        payment.refund(10000L); // now PARTIALLY_REFUNDED, 20000 remaining
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        paymentRefundService.handleOrderCancelled("order-1");

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.REFUNDED);
        verify(paymentEventPublisher).publishPaymentRefunded(any());
    }

    // ── voidPayment direct unit ─────────────────────────────────────────────

    @Test
    @DisplayName("voidPayment: PENDING → VOIDED 저장, PG/이벤트 없음")
    void voidPayment_pending_savesVoided() {
        Payment payment = pendingPayment();
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        paymentRefundService.voidPayment("order-1");

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.VOIDED);
        verify(paymentGateway, never()).cancelPayment(any(), any());
        verify(paymentEventPublisher, never()).publishPaymentRefunded(any());
    }

    @Test
    @DisplayName("voidPayment: 이미 VOIDED 면 멱등 no-op (저장 없음)")
    void voidPayment_alreadyVoided_isNoOp() {
        Payment payment = pendingPayment();
        payment.voidForOrderCancelled();
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));

        paymentRefundService.voidPayment("order-1");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("voidPayment: Payment 없으면 no-op")
    void voidPayment_noPayment_isNoOp() {
        given(paymentRepository.findByOrderId("order-x")).willReturn(Optional.empty());

        paymentRefundService.voidPayment("order-x");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("COMPLETED 결제 환불 시 PG cancel 호출 후 REFUNDED 상태로 저장되고 이벤트가 발행된다")
    void refundPayment_completedWithPgKey_callsCancelAndSavesRefunded() {
        Payment payment = completedPaymentWithPgKey();
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        paymentRefundService.refundPayment("order-1");

        verify(paymentGateway).cancelPayment("pk_test_123", "Order cancelled");

        ArgumentCaptor<Payment> captor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(PaymentStatus.REFUNDED);

        ArgumentCaptor<PaymentRefundedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentRefundedEvent.class);
        verify(paymentEventPublisher).publishPaymentRefunded(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("PaymentRefunded");
    }

    @Test
    @DisplayName("paymentKey가 없는 레거시 결제 환불 시 PG cancel을 호출하지 않는다")
    void refundPayment_completedWithoutPgKey_skipsPgCancel() {
        Payment payment = completedPaymentWithoutPgKey();
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        paymentRefundService.refundPayment("order-1");

        verify(paymentGateway, never()).cancelPayment(any(), any());
        verify(paymentRepository).save(any());
        verify(paymentEventPublisher).publishPaymentRefunded(any());
    }

    @Test
    @DisplayName("이미 REFUNDED 상태이면 멱등 처리한다 (저장/이벤트 없음)")
    void refundPayment_alreadyRefunded_isIdempotent() {
        Payment payment = completedPaymentWithPgKey();
        payment.refund();
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));

        paymentRefundService.refundPayment("order-1");

        verify(paymentRepository, never()).save(any());
        verify(paymentEventPublisher, never()).publishPaymentRefunded(any());
        verify(paymentGateway, never()).cancelPayment(any(), any());
    }

    @Test
    @DisplayName("Payment가 없는 orderId이면 무시한다")
    void refundPayment_noPayment_skips() {
        given(paymentRepository.findByOrderId("order-x")).willReturn(Optional.empty());

        paymentRefundService.refundPayment("order-x");

        verify(paymentRepository, never()).save(any());
        verify(paymentEventPublisher, never()).publishPaymentRefunded(any());
    }

    @Test
    @DisplayName("PG cancel 호출 시 PgGatewayUnavailableException 발생하면 결제 상태를 변경하지 않고 propagate 한다 (ADR-MONO-005 § D4 — 전송 실패는 PG 상태 불명)")
    void refundPayment_pgGatewayUnavailable_doesNotChangeState() {
        Payment payment = completedPaymentWithPgKey();
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));
        doThrow(new PgGatewayUnavailableException("retry exhausted"))
                .when(paymentGateway).cancelPayment("pk_test_123", "Order cancelled");

        assertThatThrownBy(() -> paymentRefundService.refundPayment("order-1"))
                .isInstanceOf(PgGatewayUnavailableException.class);

        // CRITICAL: row stays in COMPLETED (no refund state change). Caller's
        // retry / DLT mechanism will re-drive when PG recovers.
        verify(paymentRepository, never()).save(any());
        verify(paymentEventPublisher, never()).publishPaymentRefunded(any());
        verify(paymentMetricRecorder, never()).incrementPaymentRefunded();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    // ── HTTP partial-refund path: refundPayment(paymentId, user, amount) ──────

    @Test
    @DisplayName("부분 환불 성공 시 PG에 cancelAmount를 전달하고 이벤트에 이번 환불액/누적/부분여부가 실린다")
    void refundPaymentByAmount_partial_publishesEventWithThisRefundAndCumulative() {
        Payment payment = completedPaymentWithPgKey(); // amount 30000
        given(paymentRepository.findById("pay-1")).willReturn(Optional.of(payment));
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        Payment result = paymentRefundService.refundPayment("pay-1", "user-1", 10000L);

        // PG partial cancel invoked with the 3-arg (key, reason, amount) overload.
        verify(paymentGateway).cancelPayment("pk_test_123", "Partial refund", 10000L);

        assertThat(result.getStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThat(result.getRefundedAmount()).isEqualTo(10000L);

        ArgumentCaptor<PaymentRefundedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentRefundedEvent.class);
        verify(paymentEventPublisher).publishPaymentRefunded(eventCaptor.capture());
        PaymentRefundedEvent.Payload p = eventCaptor.getValue().payload();
        assertThat(p.amount()).isEqualTo(10000L);        // THIS refund's amount
        assertThat(p.totalRefunded()).isEqualTo(10000L); // cumulative
        assertThat(p.fullyRefunded()).isFalse();
        verify(paymentMetricRecorder).incrementPaymentRefunded();
    }

    @Test
    @DisplayName("부분 환불이 잔여 전액과 같으면 이벤트의 fullyRefunded가 true가 된다")
    void refundPaymentByAmount_closesOut_eventFullyRefundedTrue() {
        Payment payment = completedPaymentWithPgKey(); // amount 30000
        given(paymentRepository.findById("pay-1")).willReturn(Optional.of(payment));
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        paymentRefundService.refundPayment("pay-1", "user-1", 30000L);

        ArgumentCaptor<PaymentRefundedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentRefundedEvent.class);
        verify(paymentEventPublisher).publishPaymentRefunded(eventCaptor.capture());
        PaymentRefundedEvent.Payload p = eventCaptor.getValue().payload();
        assertThat(p.amount()).isEqualTo(30000L);
        assertThat(p.totalRefunded()).isEqualTo(30000L);
        assertThat(p.fullyRefunded()).isTrue();
    }

    @Test
    @DisplayName("소유자가 아니면 UnauthorizedPaymentAccessException 발생, 환불/이벤트 없음")
    void refundPaymentByAmount_nonOwner_throwsUnauthorized() {
        Payment payment = completedPaymentWithPgKey();
        given(paymentRepository.findById("pay-1")).willReturn(Optional.of(payment));

        assertThatThrownBy(() -> paymentRefundService.refundPayment("pay-1", "attacker", 10000L))
                .isInstanceOf(UnauthorizedPaymentAccessException.class);

        verify(paymentGateway, never()).cancelPayment(any(), any(), anyLong());
        verify(paymentRepository, never()).save(any());
        verify(paymentEventPublisher, never()).publishPaymentRefunded(any());
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.COMPLETED);
    }

    @Test
    @DisplayName("존재하지 않는 paymentId이면 PaymentNotFoundException 발생")
    void refundPaymentByAmount_unknownId_throwsPaymentNotFound() {
        given(paymentRepository.findById("missing")).willReturn(Optional.empty());

        assertThatThrownBy(() -> paymentRefundService.refundPayment("missing", "user-1", 10000L))
                .isInstanceOf(PaymentNotFoundException.class);

        verify(paymentRepository, never()).save(any());
        verify(paymentEventPublisher, never()).publishPaymentRefunded(any());
    }
}
