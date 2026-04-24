package com.example.payment.adapter.in.event;

import com.example.payment.application.service.PaymentProcessingService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPlacedEventConsumer 단위 테스트")
class OrderPlacedEventConsumerTest {

    @InjectMocks
    private OrderPlacedEventConsumer consumer;

    @Mock
    private PaymentProcessingService paymentProcessingService;

    @Mock
    private ObjectMapper objectMapper;

    private OrderPlacedEvent event(String orderId, String userId, long totalPrice) {
        return new OrderPlacedEvent(
                UUID.randomUUID().toString(), "OrderPlaced", "2026-03-23T00:00:00", "order-service",
                new OrderPlacedEvent.OrderPlacedPayload(orderId, userId, totalPrice, List.of(), null)
        );
    }

    @Test
    @DisplayName("정상 이벤트 수신 시 processPayment를 호출한다")
    void handle_validEvent_callsProcessPayment() {
        consumer.handle(event("order-1", "user-1", 30000L));

        verify(paymentProcessingService).processPayment("order-1", "user-1", 30000L);
    }

    @Test
    @DisplayName("amount가 0인 이벤트 수신 시 processPayment를 호출하지 않는다")
    void handle_zeroAmount_skips() {
        consumer.handle(event("order-1", "user-1", 0L));

        verifyNoInteractions(paymentProcessingService);
    }

    @Test
    @DisplayName("amount가 음수인 이벤트 수신 시 processPayment를 호출하지 않는다")
    void handle_negativeAmount_skips() {
        consumer.handle(event("order-1", "user-1", -5000L));

        verifyNoInteractions(paymentProcessingService);
    }

    @Test
    @DisplayName("processPayment에서 예외가 발생하면 상위로 전파된다")
    void handle_processThrows_propagatesException() {
        doThrow(new RuntimeException("DB error"))
                .when(paymentProcessingService).processPayment(any(), any(), anyLong());

        assertThatThrownBy(() -> consumer.handle(event("order-1", "user-1", 30000L)))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("DB error");
    }

    @Test
    @DisplayName("payload가 null인 이벤트 수신 시 processPayment를 호출하지 않는다")
    void handle_nullPayload_skips() {
        OrderPlacedEvent nullPayloadEvent = new OrderPlacedEvent(
                UUID.randomUUID().toString(), "OrderPlaced", "2026-03-23T00:00:00", "order-service",
                null
        );

        consumer.handle(nullPayloadEvent);

        verifyNoInteractions(paymentProcessingService);
    }

    @Test
    @DisplayName("orderId가 null인 이벤트 수신 시 processPayment를 호출하지 않는다")
    void handle_nullOrderId_skips() {
        consumer.handle(event(null, "user-1", 30000L));

        verifyNoInteractions(paymentProcessingService);
    }

    @Test
    @DisplayName("userId가 null인 이벤트 수신 시 processPayment를 호출하지 않는다")
    void handle_nullUserId_skips() {
        consumer.handle(event("order-1", null, 30000L));

        verifyNoInteractions(paymentProcessingService);
    }

    @Test
    @DisplayName("역직렬화 실패 시 JsonProcessingException이 전파된다")
    void onMessage_invalidJson_throwsJsonProcessingException() throws JsonProcessingException {
        given(objectMapper.readValue("invalid", OrderPlacedEvent.class))
                .willThrow(new JsonProcessingException("parse error") {});

        assertThatThrownBy(() -> consumer.onMessage("invalid"))
                .isInstanceOf(JsonProcessingException.class);
    }
}
