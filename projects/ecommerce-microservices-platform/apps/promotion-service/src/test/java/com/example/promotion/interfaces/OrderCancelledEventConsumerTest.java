package com.example.promotion.interfaces;

import com.example.promotion.application.service.CouponCommandService;
import com.example.promotion.interfaces.event.OrderCancelledEvent;
import com.example.promotion.interfaces.event.OrderCancelledEvent.OrderCancelledPayload;
import com.example.promotion.interfaces.event.OrderCancelledEventConsumer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.anyString;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCancelledEventConsumer 단위 테스트")
class OrderCancelledEventConsumerTest {

    @Mock
    private CouponCommandService couponCommandService;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private OrderCancelledEventConsumer consumer;

    @Test
    @DisplayName("정상 이벤트 처리 시 쿠폰 복원 서비스를 호출한다")
    void handle_validEvent_callsRestoreCoupons() {
        OrderCancelledPayload payload = new OrderCancelledPayload(
                "order-1", "user-1", "2026-03-28T12:00:00Z");
        OrderCancelledEvent event = new OrderCancelledEvent(
                "event-1", "OrderCancelled", "2026-03-28T12:00:00Z", "order-service", payload);

        consumer.handle(event);

        verify(couponCommandService).restoreCouponsByOrderId("order-1");
    }

    @Test
    @DisplayName("payload가 null인 이벤트는 무시한다")
    void handle_nullPayload_skips() {
        OrderCancelledEvent event = new OrderCancelledEvent(
                "event-2", "OrderCancelled", "2026-03-28T12:00:00Z", "order-service", null);

        consumer.handle(event);

        verify(couponCommandService, never()).restoreCouponsByOrderId(anyString());
    }

    @Test
    @DisplayName("orderId가 null인 이벤트는 무시한다")
    void handle_nullOrderId_skips() {
        OrderCancelledPayload payload = new OrderCancelledPayload(
                null, "user-1", "2026-03-28T12:00:00Z");
        OrderCancelledEvent event = new OrderCancelledEvent(
                "event-3", "OrderCancelled", "2026-03-28T12:00:00Z", "order-service", payload);

        consumer.handle(event);

        verify(couponCommandService, never()).restoreCouponsByOrderId(anyString());
    }

    @Test
    @DisplayName("orderId가 blank인 이벤트는 무시한다")
    void handle_blankOrderId_skips() {
        OrderCancelledPayload payload = new OrderCancelledPayload(
                "   ", "user-1", "2026-03-28T12:00:00Z");
        OrderCancelledEvent event = new OrderCancelledEvent(
                "event-4", "OrderCancelled", "2026-03-28T12:00:00Z", "order-service", payload);

        consumer.handle(event);

        verify(couponCommandService, never()).restoreCouponsByOrderId(anyString());
    }
}
