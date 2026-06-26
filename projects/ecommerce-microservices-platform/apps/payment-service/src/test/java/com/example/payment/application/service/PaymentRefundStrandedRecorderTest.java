package com.example.payment.application.service;

import com.example.payment.application.event.PaymentRefundStrandedEvent;
import com.example.payment.application.port.out.PaymentEventPublisher;
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
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentRefundStrandedRecorder 단위 테스트 (TASK-BE-437 + TASK-BE-438)")
class PaymentRefundStrandedRecorderTest {

    private static final Instant NOW = Instant.parse("2026-06-26T10:00:00Z");

    @Mock
    private PaymentEventPublisher paymentEventPublisher;

    @Mock
    private StrandedRefundRepository strandedRefundRepository;

    private PaymentRefundStrandedRecorder recorder;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        recorder = new PaymentRefundStrandedRecorder(paymentEventPublisher, strandedRefundRepository, clock);
    }

    @Test
    @DisplayName("record(...) 호출 시 paymentId/orderId/paymentKey/amount/reason 를 담은 "
            + "PaymentRefundStranded 이벤트를 outbox publisher 로 발행한다")
    void record_publishesStrandedEscalationEvent() {
        given(strandedRefundRepository.findOpenByPaymentId("pay-1")).willReturn(Optional.empty());

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

    @Test
    @DisplayName("AC-1: 신규 stranding 시 STRANDED 행(attempts=0, next_attempt_at≈now)을 같은 REQUIRES_NEW 에서 영속한다")
    void record_persistsStrandedRefundRow() {
        given(strandedRefundRepository.findOpenByPaymentId("pay-1")).willReturn(Optional.empty());

        recorder.record("order-1", "pay-1", "pk_test_123", 30000L, "PgConfirmFailedException");

        ArgumentCaptor<StrandedRefund> captor = ArgumentCaptor.forClass(StrandedRefund.class);
        verify(strandedRefundRepository).save(captor.capture());

        StrandedRefund row = captor.getValue();
        assertThat(row.getStatus()).isEqualTo(StrandedRefundStatus.STRANDED);
        assertThat(row.getAttempts()).isZero();
        assertThat(row.getNextAttemptAt()).isEqualTo(NOW);
        assertThat(row.getPaymentId()).isEqualTo("pay-1");
        assertThat(row.getOrderId()).isEqualTo("order-1");
        assertThat(row.getPaymentKey()).isEqualTo("pk_test_123");
        assertThat(row.getAmount()).isEqualTo(30000L);
        assertThat(row.getReason()).isEqualTo("PgConfirmFailedException");
        assertThat(row.getCreatedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("AC-1 dedupe: 동일 payment_id 의 open 행이 이미 있으면 두 번째 open 행을 만들지 않는다 (이벤트는 재발행 허용)")
    void record_dedupesOpenRowByPaymentId() {
        StrandedRefund existing = StrandedRefund.open(
                "pay-1", "order-1", "pk_test_123", 30000L, "PgGatewayUnavailableException", NOW);
        given(strandedRefundRepository.findOpenByPaymentId("pay-1")).willReturn(Optional.of(existing));

        recorder.record("order-1", "pay-1", "pk_test_123", 30000L, "PgGatewayUnavailableException");

        // No second open row is created…
        verify(strandedRefundRepository, never()).save(any());
        // …but the escalation event still re-emits (the alert consumer dedupes on paymentId/event_id).
        verify(paymentEventPublisher).publishPaymentRefundStranded(any());
    }
}
