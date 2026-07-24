package com.example.payment.application.service;

import com.example.libs.payment.PaymentAuthorization;
import com.example.libs.payment.PaymentGatewayPort;
import com.example.libs.payment.PaymentVerificationRequest;
import com.example.libs.payment.RefundablePaymentGateway;
import com.example.payment.application.port.out.PaymentEventPublisher;
import com.example.payment.domain.model.Payment;
import com.example.payment.application.port.out.PaymentRepository;
import com.example.payment.adapter.out.metrics.PaymentMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("이벤트 발행 위임 테스트")
class PaymentTopicConfigTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    @Mock
    private PaymentMetrics paymentMetrics;

    @Mock
    private PaymentGatewayPort paymentGateway;

    @Mock
    private RefundablePaymentGateway paymentRefundGateway;

    @Mock
    private PaymentRefundStrandedRecorder paymentRefundStrandedRecorder;

    @Mock
    private com.example.payment.application.port.out.RefundRequestRepository refundRequestRepository;

    @Test
    @DisplayName("PaymentConfirmService는 PaymentEventPublisher.publishPaymentCompleted를 호출한다")
    void confirmPayment_delegatesToEventPublisher() {
        PaymentConfirmService service = new PaymentConfirmService(
                paymentRepository, paymentGateway, paymentRefundGateway, paymentEventPublisher,
                paymentMetrics, paymentRefundStrandedRecorder
        );

        Payment payment = Payment.create("order-1", "user-1", 10000L);
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));
        given(paymentGateway.verify(new PaymentVerificationRequest("pk_test", 10000L, "KRW", "order-1")))
                .willReturn(PaymentAuthorization.approved("pk_test", "CARD", null));
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        service.confirm("user-1", "pk_test", "order-1", 10000L);

        ArgumentCaptor<com.example.payment.application.event.PaymentCompletedEvent> captor =
                ArgumentCaptor.forClass(com.example.payment.application.event.PaymentCompletedEvent.class);
        verify(paymentEventPublisher).publishPaymentCompleted(captor.capture());
        assertThat(captor.getValue().payload().orderId()).isEqualTo("order-1");
    }

    @Test
    @DisplayName("PaymentRefundService는 PaymentEventPublisher.publishPaymentRefunded를 호출한다")
    void refundPayment_delegatesToEventPublisher() {
        // The full-refund (OrderCancelled) path needs no idempotency key and never touches
        // the partial-refund dedupe store (TASK-BE-535 AC-3) — the collaborator is supplied
        // only to satisfy the constructor.
        PaymentRefundService service = new PaymentRefundService(
                paymentRepository, paymentEventPublisher, paymentMetrics, paymentRefundGateway,
                refundRequestRepository, java.time.Clock.systemUTC()
        );

        Payment payment = Payment.create("order-1", "user-1", 10000L);
        payment.confirm("pk_test", "CARD", null);
        given(paymentRepository.findByOrderId("order-1")).willReturn(Optional.of(payment));
        given(paymentRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        service.refundPayment("order-1");

        ArgumentCaptor<com.example.payment.application.event.PaymentRefundedEvent> captor =
                ArgumentCaptor.forClass(com.example.payment.application.event.PaymentRefundedEvent.class);
        verify(paymentEventPublisher).publishPaymentRefunded(captor.capture());
        assertThat(captor.getValue().payload().orderId()).isEqualTo("order-1");
    }
}
