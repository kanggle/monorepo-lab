package com.example.order.application.service;

import com.example.order.application.dto.CancelOrderResult;
import com.example.order.application.event.OrderCancelledEvent;
import com.example.order.application.exception.UnauthorizedOrderAccessException;
import com.example.order.application.port.OrderEventPublisher;
import com.example.order.application.port.OrderMetricsPort;
import com.example.order.domain.exception.OrderNotFoundException;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.model.ShippingAddress;
import com.example.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderCancellationService 단위 테스트")
class OrderCancellationServiceTest {

    @InjectMocks
    private OrderCancellationService orderCancellationService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @Mock
    private OrderMetricsPort orderMetrics;

    @Mock
    private Clock clock;

    private static final Instant FIXED_NOW = Instant.parse("2026-03-25T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private static final ShippingAddress ADDRESS = new ShippingAddress(
            "홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"
    );

    @Test
    @DisplayName("PENDING 주문 취소 시 CANCELLED 상태로 변경되고 이벤트가 발행된다")
    void cancelOrder_pendingOrder_returnsCancelledAndPublishesEvent() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        given(orderRepository.findById(order.getOrderId())).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        CancelOrderResult result = orderCancellationService.cancelOrder(order.getOrderId(), "user1");

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED.name());

        ArgumentCaptor<OrderCancelledEvent> eventCaptor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(orderEventPublisher).publishOrderCancelled(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("OrderCancelled");
        assertThat(eventCaptor.getValue().payload().orderId()).isEqualTo(order.getOrderId());
    }

    @Test
    @DisplayName("다른 사용자가 취소하면 UnauthorizedOrderAccessException이 발생한다")
    void cancelOrder_differentUser_throwsUnauthorized() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        given(orderRepository.findById(order.getOrderId())).willReturn(Optional.of(order));

        assertThatThrownBy(() -> orderCancellationService.cancelOrder(order.getOrderId(), "user2"))
                .isInstanceOf(UnauthorizedOrderAccessException.class);

        verify(orderRepository, never()).save(any());
        verify(orderEventPublisher, never()).publishOrderCancelled(any(OrderCancelledEvent.class));
    }

    @Test
    @DisplayName("존재하지 않는 orderId 취소 시 OrderNotFoundException이 발생한다")
    void cancelOrder_notFound_throwsOrderNotFoundException() {
        given(orderRepository.findById("nonexistent")).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderCancellationService.cancelOrder("nonexistent", "user1"))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    @DisplayName("주문 취소 시 메트릭이 기록된다")
    void cancelOrder_validOrder_recordsMetrics() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        given(orderRepository.findById(order.getOrderId())).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        orderCancellationService.cancelOrder(order.getOrderId(), "user1");

        verify(orderMetrics).recordOrderCancelled("user");
        verify(orderMetrics).recordStatusTransition("PENDING", "CANCELLED");
    }
}
