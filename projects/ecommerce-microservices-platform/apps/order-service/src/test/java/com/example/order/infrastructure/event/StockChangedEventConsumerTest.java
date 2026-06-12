package com.example.order.infrastructure.event;

import com.example.order.application.service.OrderConfirmationService;
import com.example.order.domain.exception.OrderNotFoundException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("StockChangedEventConsumer 단위 테스트")
class StockChangedEventConsumerTest {

    @InjectMocks
    private StockChangedEventConsumer consumer;

    @Mock
    private OrderConfirmationService orderConfirmationService;

    @Mock
    private EventDeduplicationChecker eventDeduplicationChecker;

    @Mock
    private ObjectMapper objectMapper;

    private StockChangedEvent event(String reason, String orderId) {
        return new StockChangedEvent(
                UUID.randomUUID().toString(),
                "StockChanged",
                "2026-03-23T00:00:00",
                "product-service",
                "ecommerce",
                new StockChangedEvent.StockChangedPayload("p1", "v1", 10, 9, -1, reason, orderId)
        );
    }

    @Test
    @DisplayName("ORDER_RESERVED 이벤트와 orderId가 있으면 confirmOrder를 호출한다")
    void handle_orderReserved_callsConfirmOrder() {
        consumer.handle(event("ORDER_RESERVED", "order-123"));

        verify(orderConfirmationService).confirmOrder("order-123");
    }

    @Test
    @DisplayName("RESTOCK reason이면 confirmOrder를 호출하지 않는다")
    void handle_restock_doesNotCallConfirmOrder() {
        consumer.handle(event("RESTOCK", null));

        verify(orderConfirmationService, never()).confirmOrder(any());
    }

    @Test
    @DisplayName("ORDER_CANCELLED reason이면 confirmOrder를 호출하지 않는다")
    void handle_orderCancelled_doesNotCallConfirmOrder() {
        consumer.handle(event("ORDER_CANCELLED", "order-123"));

        verify(orderConfirmationService, never()).confirmOrder(any());
    }

    @Test
    @DisplayName("ORDER_RESERVED 이벤트에 orderId가 없으면 confirmOrder를 호출하지 않는다")
    void handle_orderReservedWithoutOrderId_doesNotCallConfirmOrder() {
        consumer.handle(event("ORDER_RESERVED", null));

        verify(orderConfirmationService, never()).confirmOrder(any());
    }

    @Test
    @DisplayName("ORDER_RESERVED 이벤트에 orderId가 blank이면 confirmOrder를 호출하지 않는다")
    void handle_orderReservedWithBlankOrderId_doesNotCallConfirmOrder() {
        consumer.handle(event("ORDER_RESERVED", "  "));

        verify(orderConfirmationService, never()).confirmOrder(any());
    }

    @Test
    @DisplayName("confirmOrder에서 예외가 발생하면 외부로 전파된다 (DLQ 라우팅)")
    void handle_confirmOrderThrows_propagatesException() {
        doThrow(new OrderNotFoundException("order-123"))
                .when(orderConfirmationService).confirmOrder("order-123");

        assertThatThrownBy(() -> consumer.handle(event("ORDER_RESERVED", "order-123")))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("중복 이벤트 수신 시 서비스를 호출하지 않는다")
    void handle_duplicateEvent_doesNotCallService() {
        StockChangedEvent event = event("ORDER_RESERVED", "order-123");
        when(eventDeduplicationChecker.isDuplicate(event.eventId(), "StockChanged")).thenReturn(true);

        consumer.handle(event);

        verify(orderConfirmationService, never()).confirmOrder(any());
    }

    @Test
    @DisplayName("역직렬화 실패 시 JsonProcessingException이 래핑 없이 직접 전파된다 (DLQ 라우팅)")
    void onMessage_deserializationFails_throwsJsonProcessingException() throws Exception {
        when(objectMapper.readValue(anyString(), eq(StockChangedEvent.class)))
                .thenThrow(new com.fasterxml.jackson.databind.JsonMappingException(null, "invalid"));

        assertThatThrownBy(() -> consumer.onMessage("invalid-json"))
                .isInstanceOf(JsonProcessingException.class);
    }
}
