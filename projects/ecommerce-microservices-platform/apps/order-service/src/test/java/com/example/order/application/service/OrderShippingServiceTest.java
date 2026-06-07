package com.example.order.application.service;

import com.example.order.application.port.OrderMetricsPort;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.model.ShippingAddress;
import com.example.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
@DisplayName("OrderShippingService 단위 테스트")
class OrderShippingServiceTest {

    @InjectMocks
    private OrderShippingService orderShippingService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderMetricsPort orderMetrics;

    @Mock
    private Clock clock;

    private static final Instant FIXED_NOW = Instant.parse("2026-06-08T10:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

    private static final ShippingAddress ADDRESS = new ShippingAddress(
            "홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호");

    private Order confirmedOrder() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        order.confirm(FIXED_CLOCK);
        return order;
    }

    @Test
    @DisplayName("CONFIRMED 주문에 SHIPPED 적용 시 SHIPPED로 전이된다")
    void markShipped_confirmedOrder_becomesShipped() {
        Order order = confirmedOrder();
        given(orderRepository.findById(order.getOrderId())).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        orderShippingService.markShipped(order.getOrderId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(orderRepository).save(order);
        verify(orderMetrics).recordStatusTransition("CONFIRMED", "SHIPPED");
    }

    @Test
    @DisplayName("이미 SHIPPED인 주문은 멱등하게 처리되고 메트릭이 기록되지 않는다")
    void markShipped_alreadyShipped_idempotent() {
        Order order = confirmedOrder();
        order.ship(FIXED_CLOCK);
        given(orderRepository.findById(order.getOrderId())).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        orderShippingService.markShipped(order.getOrderId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
        verify(orderRepository).save(order);
        verify(orderMetrics, never()).recordStatusTransition(any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 주문은 무시된다")
    void markShipped_notFound_skips() {
        given(orderRepository.findById("nope")).willReturn(Optional.empty());

        orderShippingService.markShipped("nope");

        verify(orderRepository, never()).save(any());
        verify(orderMetrics, never()).recordStatusTransition(any(), any());
    }

    @Test
    @DisplayName("PENDING 주문에 SHIPPED 적용 시 invalid 상태로 무시된다")
    void markShipped_pendingOrder_swallowsInvalidState() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        given(orderRepository.findById(order.getOrderId())).willReturn(Optional.of(order));

        orderShippingService.markShipped(order.getOrderId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository, never()).save(any());
    }
}
