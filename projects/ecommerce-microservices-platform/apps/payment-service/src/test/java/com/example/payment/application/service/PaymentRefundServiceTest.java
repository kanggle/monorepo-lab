package com.example.payment.application.service;

import com.example.payment.application.event.PaymentRefundedEvent;
import com.example.payment.application.exception.PgGatewayUnavailableException;
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
                "pay-1", "order-1", "user-1", "ecommerce", 30000L,
                PaymentStatus.COMPLETED,
                LocalDateTime.now(), LocalDateTime.now(), null,
                "pk_test_123", "CARD", null
        );
    }

    private Payment completedPaymentWithoutPgKey() {
        return Payment.reconstitute(
                "pay-1", "order-1", "user-1", "ecommerce", 30000L,
                PaymentStatus.COMPLETED,
                LocalDateTime.now(), LocalDateTime.now(), null,
                null, null, null
        );
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
}
