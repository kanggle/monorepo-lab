package com.example.order.infrastructure.event;

import com.example.order.application.service.OrderShippingService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("ShippingStatusChangedEventConsumer 단위 테스트")
class ShippingStatusChangedEventConsumerTest {

    @InjectMocks
    private ShippingStatusChangedEventConsumer consumer;

    @Mock
    private OrderShippingService orderShippingService;

    @Mock
    private EventDeduplicationChecker eventDeduplicationChecker;

    @Mock
    private ObjectMapper objectMapper;

    private ShippingStatusChangedEvent event(String orderId, String newStatus) {
        return new ShippingStatusChangedEvent(
                UUID.randomUUID().toString(),
                "ShippingStatusChanged",
                "2026-06-08T10:00:00Z",
                "shipping-service",
                new ShippingStatusChangedEvent.ShippingStatusChangedPayload(
                        "ship-1", orderId, "user-1", "PREPARING", newStatus,
                        "SHP-1", "CJ-LOGISTICS", "2026-06-08T10:00:00Z")
        );
    }

    @Test
    @DisplayName("newStatus=SHIPPED 이벤트 수신 시 markShipped 호출")
    void handle_shipped_callsMarkShipped() {
        ShippingStatusChangedEvent event = event("order-1", "SHIPPED");
        given(eventDeduplicationChecker.isDuplicate(event.eventId(), "ShippingStatusChanged"))
                .willReturn(false);

        consumer.handle(event);

        verify(orderShippingService).markShipped("order-1");
    }

    @Test
    @DisplayName("newStatus=DELIVERED 이벤트 수신 시 markDelivered 호출 (TASK-BE-429)")
    void handle_delivered_callsMarkDelivered() {
        ShippingStatusChangedEvent event = event("order-1", "DELIVERED");
        given(eventDeduplicationChecker.isDuplicate(event.eventId(), "ShippingStatusChanged"))
                .willReturn(false);

        consumer.handle(event);

        verify(orderShippingService).markDelivered("order-1");
        verify(orderShippingService, never()).markShipped(any());
    }

    @Test
    @DisplayName("newStatus가 SHIPPED/DELIVERED가 아니면(IN_TRANSIT) 둘 다 무시된다")
    void handle_nonShipped_skips() {
        ShippingStatusChangedEvent event = event("order-1", "IN_TRANSIT");
        given(eventDeduplicationChecker.isDuplicate(event.eventId(), "ShippingStatusChanged"))
                .willReturn(false);

        consumer.handle(event);

        verify(orderShippingService, never()).markShipped(any());
        verify(orderShippingService, never()).markDelivered(any());
    }

    @Test
    @DisplayName("중복 이벤트는 무시된다")
    void handle_duplicate_skips() {
        ShippingStatusChangedEvent event = event("order-1", "SHIPPED");
        given(eventDeduplicationChecker.isDuplicate(event.eventId(), "ShippingStatusChanged"))
                .willReturn(true);

        consumer.handle(event);

        verify(orderShippingService, never()).markShipped(any());
    }

    @Test
    @DisplayName("payload가 null이면 무시된다")
    void handle_nullPayload_skips() {
        ShippingStatusChangedEvent event = new ShippingStatusChangedEvent(
                UUID.randomUUID().toString(), "ShippingStatusChanged",
                "2026-06-08T10:00:00Z", "shipping-service", null);
        given(eventDeduplicationChecker.isDuplicate(event.eventId(), "ShippingStatusChanged"))
                .willReturn(false);

        consumer.handle(event);

        verify(orderShippingService, never()).markShipped(any());
    }

    @Test
    @DisplayName("orderId가 없으면 무시된다")
    void handle_missingOrderId_skips() {
        ShippingStatusChangedEvent event = event("  ", "SHIPPED");
        given(eventDeduplicationChecker.isDuplicate(event.eventId(), "ShippingStatusChanged"))
                .willReturn(false);

        consumer.handle(event);

        verify(orderShippingService, never()).markShipped(any());
    }
}
