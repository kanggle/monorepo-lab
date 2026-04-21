package com.example.order.infrastructure.event;

import com.example.order.application.service.PaymentConfirmationService;
import com.example.order.domain.exception.InvalidOrderException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentCompletedEventConsumer 단위 테스트")
class PaymentCompletedEventConsumerTest {

    @InjectMocks
    private PaymentCompletedEventConsumer consumer;

    @Mock
    private PaymentConfirmationService paymentConfirmationService;

    @Mock
    private EventDeduplicationChecker eventDeduplicationChecker;

    @Mock
    private ObjectMapper objectMapper;

    private PaymentCompletedEvent event(String orderId, String paymentId, String paidAt) {
        return new PaymentCompletedEvent(
                UUID.randomUUID().toString(),
                "PaymentCompleted",
                "2026-03-24T00:00:00Z",
                "payment-service",
                new PaymentCompletedEvent.PaymentCompletedPayload(
                        paymentId, orderId, "user-1", 50000L, paidAt
                )
        );
    }

    @Test
    @DisplayName("정상 이벤트 수신 시 markPaymentCompleted를 호출한다")
    void handle_validEvent_callsMarkPaymentCompleted() {
        consumer.handle(event("order-123", "pay-456", "2026-03-24T10:00:00Z"));

        verify(paymentConfirmationService).markPaymentCompleted(
                eq("order-123"), eq("pay-456"),
                eq(Instant.parse("2026-03-24T10:00:00Z"))
        );
    }

    @Test
    @DisplayName("UTC 타임스탬프가 Instant로 정확히 변환된다")
    void handle_utcTimestamp_convertsToInstant() {
        consumer.handle(event("order-123", "pay-456", "2026-03-24T23:30:00Z"));

        verify(paymentConfirmationService).markPaymentCompleted(
                eq("order-123"), eq("pay-456"),
                eq(Instant.parse("2026-03-24T23:30:00Z"))
        );
    }

    @Test
    @DisplayName("payload가 null이면 서비스를 호출하지 않는다")
    void handle_nullPayload_doesNotCallService() {
        PaymentCompletedEvent event = new PaymentCompletedEvent(
                UUID.randomUUID().toString(), "PaymentCompleted",
                "2026-03-24T00:00:00Z", "payment-service", null
        );

        consumer.handle(event);

        verify(paymentConfirmationService, never()).markPaymentCompleted(any(), any(), any());
    }

    @Test
    @DisplayName("orderId가 null이면 서비스를 호출하지 않는다")
    void handle_nullOrderId_doesNotCallService() {
        consumer.handle(event(null, "pay-456", "2026-03-24T10:00:00Z"));

        verify(paymentConfirmationService, never()).markPaymentCompleted(any(), any(), any());
    }

    @Test
    @DisplayName("orderId가 blank이면 서비스를 호출하지 않는다")
    void handle_blankOrderId_doesNotCallService() {
        consumer.handle(event("  ", "pay-456", "2026-03-24T10:00:00Z"));

        verify(paymentConfirmationService, never()).markPaymentCompleted(any(), any(), any());
    }

    @Test
    @DisplayName("paymentId가 null이면 서비스를 호출하지 않는다")
    void handle_nullPaymentId_doesNotCallService() {
        consumer.handle(event("order-123", null, "2026-03-24T10:00:00Z"));

        verify(paymentConfirmationService, never()).markPaymentCompleted(any(), any(), any());
    }

    @Test
    @DisplayName("paymentId가 blank이면 서비스를 호출하지 않는다")
    void handle_blankPaymentId_doesNotCallService() {
        consumer.handle(event("order-123", "  ", "2026-03-24T10:00:00Z"));

        verify(paymentConfirmationService, never()).markPaymentCompleted(any(), any(), any());
    }

    @Test
    @DisplayName("paidAt이 null이면 예외가 전파된다 (DLQ 라우팅)")
    void handle_nullPaidAt_throwsException() {
        assertThatThrownBy(() ->
                consumer.handle(event("order-123", "pay-456", null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(paymentConfirmationService, never()).markPaymentCompleted(any(), any(), any());
    }

    @Test
    @DisplayName("paidAt이 파싱 불가능하면 예외가 전파된다 (DLQ 라우팅)")
    void handle_invalidPaidAt_throwsException() {
        assertThatThrownBy(() ->
                consumer.handle(event("order-123", "pay-456", "not-a-date")))
                .isInstanceOf(IllegalArgumentException.class);

        verify(paymentConfirmationService, never()).markPaymentCompleted(any(), any(), any());
    }

    @Test
    @DisplayName("서비스에서 예외가 발생하면 외부로 전파된다 (DLQ 라우팅)")
    void handle_serviceThrows_propagatesException() {
        doThrow(new InvalidOrderException("취소된 주문"))
                .when(paymentConfirmationService).markPaymentCompleted(any(), any(), any());

        assertThatThrownBy(() ->
                consumer.handle(event("order-123", "pay-456", "2026-03-24T10:00:00Z")))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    @DisplayName("중복 이벤트 수신 시 서비스를 호출하지 않는다")
    void handle_duplicateEvent_doesNotCallService() {
        PaymentCompletedEvent event = event("order-123", "pay-456", "2026-03-24T10:00:00Z");
        when(eventDeduplicationChecker.isDuplicate(event.eventId(), "PaymentCompleted")).thenReturn(true);

        consumer.handle(event);

        verify(paymentConfirmationService, never()).markPaymentCompleted(any(), any(), any());
    }

    @Test
    @DisplayName("역직렬화 실패 시 JsonProcessingException이 래핑 없이 직접 전파된다 (DLQ 라우팅)")
    void onMessage_deserializationFails_throwsJsonProcessingException() throws Exception {
        when(objectMapper.readValue(anyString(), eq(PaymentCompletedEvent.class)))
                .thenThrow(new com.fasterxml.jackson.databind.JsonMappingException(null, "invalid"));

        assertThatThrownBy(() -> consumer.onMessage("invalid-json"))
                .isInstanceOf(JsonProcessingException.class);
    }
}
