package com.example.payment.application.service;

import com.example.payment.application.event.PaymentRefundStrandedEvent;
import com.example.payment.application.port.out.PaymentEventPublisher;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentRefundStrandedRecorder 단위 테스트 (TASK-BE-437)")
class PaymentRefundStrandedRecorderTest {

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    @InjectMocks
    private PaymentRefundStrandedRecorder recorder;

    @Test
    @DisplayName("record(...) 호출 시 paymentId/orderId/paymentKey/amount/reason 를 담은 "
            + "PaymentRefundStranded 이벤트를 outbox publisher 로 발행한다")
    void record_publishesStrandedEscalationEvent() {
        recorder.record("order-1", "pay-1", "pk_test_123", 30000L, "PgGatewayUnavailableException");

        ArgumentCaptor<PaymentRefundStrandedEvent> captor =
                ArgumentCaptor.forClass(PaymentRefundStrandedEvent.class);
        verify(paymentEventPublisher).publishPaymentRefundStranded(captor.capture());

        PaymentRefundStrandedEvent event = captor.getValue();
        assertThat(event.eventType()).isEqualTo("PaymentRefundStranded");
        assertThat(event.source()).isEqualTo("payment-service");
        assertThat(event.eventId()).isNotBlank();
        assertThat(event.occurredAt()).isNotBlank();
        // No tenant bound on the test thread → default tenant (D8 net-zero).
        assertThat(event.tenantId()).isEqualTo("ecommerce");

        PaymentRefundStrandedEvent.Payload payload = event.payload();
        assertThat(payload.paymentId()).isEqualTo("pay-1");
        assertThat(payload.orderId()).isEqualTo("order-1");
        assertThat(payload.paymentKey()).isEqualTo("pk_test_123");
        assertThat(payload.amount()).isEqualTo(30000L);
        assertThat(payload.reason()).isEqualTo("PgGatewayUnavailableException");
        assertThat(payload.occurredAt()).isNotBlank();
    }
}
