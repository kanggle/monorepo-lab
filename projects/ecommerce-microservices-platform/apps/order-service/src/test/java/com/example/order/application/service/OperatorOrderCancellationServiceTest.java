package com.example.order.application.service;

import com.example.order.application.dto.CancelOrderResult;
import com.example.order.application.event.OrderCancelledEvent;
import com.example.order.application.port.OrderEventPublisher;
import com.example.order.application.port.OrderMetricsPort;
import com.example.order.domain.exception.OrderCannotBeCancelledException;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OperatorOrderCancellationService 단위 테스트 (TASK-BE-428)")
class OperatorOrderCancellationServiceTest {

    @InjectMocks
    private OperatorOrderCancellationService service;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @Mock
    private OrderMetricsPort orderMetrics;

    @Mock
    private Clock clock;

    private static final Instant FIXED_NOW = Instant.parse("2026-06-23T10:00:00Z");
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
            case BACKORDERED -> order.markBackordered(FIXED_CLOCK);
            case SHIPPED -> { order.confirm(FIXED_CLOCK); order.ship(FIXED_CLOCK); }
            default -> throw new IllegalArgumentException("unsupported: " + target);
        }
        return order;
    }

    @Test
    @DisplayName("BACKORDERED 주문을 운영자가 취소하면 CANCELLED + order.cancelled 발행 (환불 fan-out 재사용)")
    void backorderedOrder_cancelledAndEventPublished() {
        Order order = order(OrderStatus.BACKORDERED);
        given(orderRepository.findByIdAcrossTenants(order.getOrderId())).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        CancelOrderResult result = service.cancel(order.getOrderId(), "backorder never replenished");

        assertThat(result.orderId()).isEqualTo(order.getOrderId());
        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED.name());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderMetrics).recordOrderCancelled("operator");
        verify(orderMetrics).recordStatusTransition("BACKORDERED", "CANCELLED");

        ArgumentCaptor<OrderCancelledEvent> captor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(orderEventPublisher).publishOrderCancelled(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo("OrderCancelled");
        assertThat(captor.getValue().payload().orderId()).isEqualTo(order.getOrderId());
    }

    @Test
    @DisplayName("PENDING 주문도 운영자 취소 가능하다")
    void pendingOrder_cancellable() {
        Order order = order(OrderStatus.PENDING);
        given(orderRepository.findByIdAcrossTenants(order.getOrderId())).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        service.cancel(order.getOrderId(), null);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderEventPublisher).publishOrderCancelled(any());
    }

    @Test
    @DisplayName("존재하지 않는 주문은 OrderNotFoundException 을 던진다")
    void unknownOrder_throwsNotFound() {
        given(orderRepository.findByIdAcrossTenants("nope")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.cancel("nope", null))
                .isInstanceOf(OrderNotFoundException.class);

        verify(orderRepository, never()).save(any());
        verify(orderEventPublisher, never()).publishOrderCancelled(any());
    }

    @Test
    @DisplayName("SHIPPED 주문은 취소 불가 — OrderCannotBeCancelledException 이 도메인에서 전파된다")
    void shippedOrder_surfacesCannotBeCancelled() {
        Order order = order(OrderStatus.SHIPPED);
        given(orderRepository.findByIdAcrossTenants(order.getOrderId())).willReturn(Optional.of(order));

        assertThatThrownBy(() -> service.cancel(order.getOrderId(), null))
                .isInstanceOf(OrderCannotBeCancelledException.class);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(orderRepository, never()).save(any());
        verify(orderEventPublisher, never()).publishOrderCancelled(any());
    }
}
