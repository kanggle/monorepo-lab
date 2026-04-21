package com.example.order.infrastructure.event;

import com.example.order.application.service.PaymentRefundConfirmationService;
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
@DisplayName("PaymentRefundedEventConsumer 단위 테스트")
class PaymentRefundedEventConsumerTest {

    @InjectMocks
    private PaymentRefundedEventConsumer consumer;

    @Mock
    private PaymentRefundConfirmationService paymentRefundConfirmationService;

    @Mock
    private EventDeduplicationChecker eventDeduplicationChecker;

    @Mock
    private ObjectMapper objectMapper;

    private PaymentRefundedEvent event(String orderId, String refundedAt) {
        return new PaymentRefundedEvent(
                UUID.randomUUID().toString(),
                "PaymentRefunded",
                "2026-03-24T00:00:00Z",
                "payment-service",
                new PaymentRefundedEvent.PaymentRefundedPayload(
                        "pay-123", orderId, "user-1", 50000L, refundedAt
                )
        );
    }

    @Test
    @DisplayName("정상 이벤트 수신 시 markRefunded를 호출한다")
    void handle_validEvent_callsMarkRefunded() {
        consumer.handle(event("order-123", "2026-03-24T12:00:00Z"));

        verify(paymentRefundConfirmationService).markRefunded(
                eq("order-123"),
                eq(Instant.parse("2026-03-24T12:00:00Z"))
        );
    }

    @Test
    @DisplayName("UTC 타임스탬프가 Instant로 정확히 변환된다")
    void handle_utcTimestamp_convertsToInstant() {
        consumer.handle(event("order-123", "2026-03-24T23:30:00Z"));

        verify(paymentRefundConfirmationService).markRefunded(
                eq("order-123"),
                eq(Instant.parse("2026-03-24T23:30:00Z"))
        );
    }

    @Test
    @DisplayName("payload가 null이면 서비스를 호출하지 않는다")
    void handle_nullPayload_doesNotCallService() {
        PaymentRefundedEvent event = new PaymentRefundedEvent(
                UUID.randomUUID().toString(), "PaymentRefunded",
                "2026-03-24T00:00:00Z", "payment-service", null
        );

        consumer.handle(event);

        verify(paymentRefundConfirmationService, never()).markRefunded(any(), any());
    }

    @Test
    @DisplayName("orderId가 null이면 서비스를 호출하지 않는다")
    void handle_nullOrderId_doesNotCallService() {
        consumer.handle(event(null, "2026-03-24T12:00:00Z"));

        verify(paymentRefundConfirmationService, never()).markRefunded(any(), any());
    }

    @Test
    @DisplayName("orderId가 blank이면 서비스를 호출하지 않는다")
    void handle_blankOrderId_doesNotCallService() {
        consumer.handle(event("  ", "2026-03-24T12:00:00Z"));

        verify(paymentRefundConfirmationService, never()).markRefunded(any(), any());
    }

    @Test
    @DisplayName("refundedAt이 null이면 IllegalArgumentException이 발생한다")
    void handle_nullRefundedAt_throwsException() {
        assertThatThrownBy(() -> consumer.handle(event("order-123", null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("refundedAt is required");
    }

    @Test
    @DisplayName("refundedAt이 파싱 불가능한 값이면 IllegalArgumentException이 발생한다")
    void handle_invalidRefundedAt_throwsException() {
        assertThatThrownBy(() -> consumer.handle(event("order-123", "not-a-date")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Failed to parse refundedAt");
    }

    @Test
    @DisplayName("서비스에서 예외가 발생하면 외부로 전파된다 (DLQ 라우팅)")
    void handle_serviceThrows_propagatesException() {
        doThrow(new InvalidOrderException("CANCELLED가 아닌 상태"))
                .when(paymentRefundConfirmationService).markRefunded(any(), any());

        assertThatThrownBy(() ->
                consumer.handle(event("order-123", "2026-03-24T12:00:00Z")))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    @DisplayName("중복 이벤트 수신 시 서비스를 호출하지 않는다")
    void handle_duplicateEvent_doesNotCallService() {
        PaymentRefundedEvent event = event("order-123", "2026-03-24T12:00:00Z");
        when(eventDeduplicationChecker.isDuplicate(event.eventId(), "PaymentRefunded")).thenReturn(true);

        consumer.handle(event);

        verify(paymentRefundConfirmationService, never()).markRefunded(any(), any());
    }

    @Test
    @DisplayName("역직렬화 실패 시 JsonProcessingException이 래핑 없이 직접 전파된다 (DLQ 라우팅)")
    void onMessage_deserializationFails_throwsJsonProcessingException() throws Exception {
        when(objectMapper.readValue(anyString(), eq(PaymentRefundedEvent.class)))
                .thenThrow(new com.fasterxml.jackson.databind.JsonMappingException(null, "invalid"));

        assertThatThrownBy(() -> consumer.onMessage("invalid-json"))
                .isInstanceOf(JsonProcessingException.class);
    }
}
