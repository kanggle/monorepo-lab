package com.example.order.application.service;

import com.example.order.application.event.OrderCancelledEvent;
import com.example.order.application.port.OrderEventPublisher;
import com.example.order.application.port.OrderMetricsPort;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderBackorderCancellationService 단위 테스트 (ADR-MONO-022 §D4 v2(a))")
class OrderBackorderCancellationServiceTest {

    @InjectMocks
    private OrderBackorderCancellationService service;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @Mock
    private OrderMetricsPort orderMetrics;

    @Mock
    private Clock clock;

    private static final Instant FIXED_NOW = Instant.parse("2026-06-08T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private static final ShippingAddress ADDRESS = new ShippingAddress(
            "홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호");

    private Order order(OrderStatus target) {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        switch (target) {
            case PENDING -> { /* already PENDING */ }
            case CONFIRMED -> order.confirm(FIXED_CLOCK);
            case SHIPPED -> { order.confirm(FIXED_CLOCK); order.ship(FIXED_CLOCK); }
            case CANCELLED -> order.cancel(FIXED_CLOCK);
            default -> throw new IllegalArgumentException("unsupported: " + target);
        }
        return order;
    }

    @Test
    @DisplayName("CONFIRMED 주문은 CANCELLED 로 전이되고 order.cancelled 가 발행된다 (환불 saga 트리거)")
    void confirmedOrder_cancelledAndEventPublished() {
        Order order = order(OrderStatus.CONFIRMED);
        given(orderRepository.findById(order.getOrderId())).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        service.cancelForBackorder(order.getOrderId(), "INSUFFICIENT_STOCK");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderMetrics).recordOrderCancelled("backorder");
        verify(orderMetrics).recordStatusTransition("CONFIRMED", "CANCELLED");

        ArgumentCaptor<OrderCancelledEvent> captor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(orderEventPublisher).publishOrderCancelled(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("OrderCancelled");
        assertThat(captor.getValue().payload().orderId()).isEqualTo(order.getOrderId());
    }

    @Test
    @DisplayName("PENDING 주문도 취소 가능하다")
    void pendingOrder_cancellable() {
        Order order = order(OrderStatus.PENDING);
        given(orderRepository.findById(order.getOrderId())).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        service.cancelForBackorder(order.getOrderId(), "INSUFFICIENT_STOCK");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderEventPublisher).publishOrderCancelled(any());
    }

    @Test
    @DisplayName("이미 CANCELLED 인 주문은 멱등 no-op (재발행/이벤트 없음)")
    void alreadyCancelled_isNoOp() {
        Order order = order(OrderStatus.CANCELLED);
        given(orderRepository.findById(order.getOrderId())).willReturn(Optional.of(order));

        service.cancelForBackorder(order.getOrderId(), "INSUFFICIENT_STOCK");

        verify(orderRepository, never()).save(any());
        verify(orderEventPublisher, never()).publishOrderCancelled(any());
        verify(orderMetrics, never()).recordOrderCancelled(any());
    }

    @Test
    @DisplayName("SHIPPED 주문에 대한 backorder 는 ALERT 후 skip — 상태 불변, 이벤트 없음")
    void shippedOrder_alertAndSkip() {
        Order order = order(OrderStatus.SHIPPED);
        given(orderRepository.findById(order.getOrderId())).willReturn(Optional.of(order));

        service.cancelForBackorder(order.getOrderId(), "INSUFFICIENT_STOCK");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(orderRepository, never()).save(any());
        verify(orderEventPublisher, never()).publishOrderCancelled(any());
    }

    @Test
    @DisplayName("존재하지 않는 주문은 warn 후 skip (주문 위조 없음)")
    void unknownOrder_skips() {
        given(orderRepository.findById("nope")).willReturn(Optional.empty());

        service.cancelForBackorder("nope", "INSUFFICIENT_STOCK");

        verify(orderRepository, never()).save(any());
        verify(orderEventPublisher, never()).publishOrderCancelled(any());
    }
}
