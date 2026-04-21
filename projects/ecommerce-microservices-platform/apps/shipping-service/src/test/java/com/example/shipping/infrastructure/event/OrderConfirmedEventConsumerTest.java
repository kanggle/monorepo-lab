package com.example.shipping.infrastructure.event;

import com.example.shipping.application.command.CreateShippingCommand;
import com.example.shipping.application.service.ShippingCommandService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderConfirmedEventConsumer 단위 테스트")
class OrderConfirmedEventConsumerTest {

    @InjectMocks
    private OrderConfirmedEventConsumer consumer;

    @Mock
    private ShippingCommandService shippingCommandService;

    @Mock
    private EventDeduplicationChecker eventDeduplicationChecker;

    @Mock
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("유효한 OrderConfirmed 이벤트 처리 시 배송 생성")
    void handle_validEvent_createsShipping() {
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                "evt-1", "OrderConfirmed", "2026-01-01T00:00:00Z", "order-service",
                new OrderConfirmedEvent.OrderConfirmedPayload("order-1", "user-1", "2026-01-01T00:00:00Z")
        );
        given(eventDeduplicationChecker.isDuplicate("evt-1", "OrderConfirmed")).willReturn(false);

        consumer.handle(event);

        verify(shippingCommandService).createShipping(new CreateShippingCommand("order-1", "user-1"));
    }

    @Test
    @DisplayName("중복 이벤트는 무시된다")
    void handle_duplicateEvent_skips() {
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                "evt-1", "OrderConfirmed", "2026-01-01T00:00:00Z", "order-service",
                new OrderConfirmedEvent.OrderConfirmedPayload("order-1", "user-1", "2026-01-01T00:00:00Z")
        );
        given(eventDeduplicationChecker.isDuplicate("evt-1", "OrderConfirmed")).willReturn(true);

        consumer.handle(event);

        verify(shippingCommandService, never()).createShipping(any());
    }

    @Test
    @DisplayName("payload가 null이면 무시된다")
    void handle_nullPayload_skips() {
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                "evt-2", "OrderConfirmed", "2026-01-01T00:00:00Z", "order-service", null);
        given(eventDeduplicationChecker.isDuplicate("evt-2", "OrderConfirmed")).willReturn(false);

        consumer.handle(event);

        verify(shippingCommandService, never()).createShipping(any());
    }

    @Test
    @DisplayName("orderId가 없으면 무시된다")
    void handle_missingOrderId_skips() {
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                "evt-3", "OrderConfirmed", "2026-01-01T00:00:00Z", "order-service",
                new OrderConfirmedEvent.OrderConfirmedPayload(null, "user-1", "2026-01-01T00:00:00Z")
        );
        given(eventDeduplicationChecker.isDuplicate("evt-3", "OrderConfirmed")).willReturn(false);

        consumer.handle(event);

        verify(shippingCommandService, never()).createShipping(any());
    }

    @Test
    @DisplayName("userId가 없으면 무시된다")
    void handle_missingUserId_skips() {
        OrderConfirmedEvent event = new OrderConfirmedEvent(
                "evt-4", "OrderConfirmed", "2026-01-01T00:00:00Z", "order-service",
                new OrderConfirmedEvent.OrderConfirmedPayload("order-1", "", "2026-01-01T00:00:00Z")
        );
        given(eventDeduplicationChecker.isDuplicate("evt-4", "OrderConfirmed")).willReturn(false);

        consumer.handle(event);

        verify(shippingCommandService, never()).createShipping(any());
    }
}
