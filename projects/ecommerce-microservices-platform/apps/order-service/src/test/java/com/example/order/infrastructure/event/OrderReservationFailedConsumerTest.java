package com.example.order.infrastructure.event;

import com.example.order.application.service.OrderBackorderService;
import com.example.order.domain.exception.OrderNotFoundException;
import com.example.order.domain.tenant.TenantContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderReservationFailedConsumer 단위 테스트 (TASK-BE-428)")
class OrderReservationFailedConsumerTest {

    @InjectMocks
    private OrderReservationFailedConsumer consumer;

    @Mock
    private OrderBackorderService orderBackorderService;

    @Mock
    private EventDeduplicationChecker eventDeduplicationChecker;

    @Mock
    private ObjectMapper objectMapper;

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    private OrderReservationFailedEvent event(String orderId) {
        return new OrderReservationFailedEvent(
                UUID.randomUUID().toString(),
                "OrderReservationFailed",
                "2026-06-23T00:00:00Z",
                "product-service",
                "ecommerce",
                new OrderReservationFailedEvent.OrderReservationFailedPayload(
                        orderId, "INSUFFICIENT_STOCK",
                        List.of(new OrderReservationFailedEvent.Shortage("v1", 5, 2)))
        );
    }

    @Test
    @DisplayName("정상 이벤트는 markBackordered(orderId) 로 위임된다")
    void validEvent_callsMarkBackordered() {
        consumer.handle(event("order-123"));

        verify(orderBackorderService).markBackordered("order-123");
    }

    @Test
    @DisplayName("중복 이벤트 수신 시 서비스를 호출하지 않는다")
    void duplicateEvent_doesNotCallService() {
        OrderReservationFailedEvent event = event("order-123");
        when(eventDeduplicationChecker.isDuplicate(event.eventId(), "OrderReservationFailed"))
                .thenReturn(true);

        consumer.handle(event);

        verify(orderBackorderService, never()).markBackordered(any());
    }

    @Test
    @DisplayName("payload 가 null 이면 무시된다")
    void nullPayload_skips() {
        OrderReservationFailedEvent event = new OrderReservationFailedEvent(
                UUID.randomUUID().toString(), "OrderReservationFailed",
                "2026-06-23T00:00:00Z", "product-service", "ecommerce", null);

        consumer.handle(event);

        verify(orderBackorderService, never()).markBackordered(any());
    }

    @Test
    @DisplayName("orderId 가 null 이면 무시된다")
    void nullOrderId_skips() {
        consumer.handle(event(null));

        verify(orderBackorderService, never()).markBackordered(any());
    }

    @Test
    @DisplayName("orderId 가 blank 이면 무시된다")
    void blankOrderId_skips() {
        consumer.handle(event("  "));

        verify(orderBackorderService, never()).markBackordered(eq("  "));
    }

    @Test
    @DisplayName("markBackordered 에서 예외가 발생하면 외부로 전파된다 (DLQ 라우팅)")
    void markBackorderedThrows_propagatesException() {
        doThrow(new OrderNotFoundException("order-123"))
                .when(orderBackorderService).markBackordered("order-123");

        assertThatThrownBy(() -> consumer.handle(event("order-123")))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("역직렬화 실패 시 JsonProcessingException이 래핑 없이 직접 전파된다 (DLQ 라우팅)")
    void onMessage_deserializationFails_throwsJsonProcessingException() throws Exception {
        when(objectMapper.readValue(anyString(), eq(OrderReservationFailedEvent.class)))
                .thenThrow(new com.fasterxml.jackson.databind.JsonMappingException(null, "invalid"));

        assertThatThrownBy(() -> consumer.onMessage("invalid-json"))
                .isInstanceOf(JsonProcessingException.class);
    }
}
