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
@DisplayName("OrderBackorderService 단위 테스트 (TASK-BE-428)")
class OrderBackorderServiceTest {

    @InjectMocks
    private OrderBackorderService service;

    @Mock
    private OrderRepository orderRepository;

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
            case CANCELLED -> order.cancel(FIXED_CLOCK);
            default -> throw new IllegalArgumentException("unsupported: " + target);
        }
        return order;
    }

    @Test
    @DisplayName("PENDING 주문은 BACKORDERED 로 전이되고 메트릭이 기록된다 (이벤트 없음)")
    void pendingOrder_transitionsToBackorderedAndRecordsMetrics() {
        Order order = order(OrderStatus.PENDING);
        given(orderRepository.findByIdAcrossTenants(order.getOrderId())).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        service.markBackordered(order.getOrderId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.BACKORDERED);
        verify(orderRepository).save(order);
        verify(orderMetrics).recordOrderBackordered();
        verify(orderMetrics).recordStatusTransition("PENDING", "BACKORDERED");
    }

    @Test
    @DisplayName("이미 BACKORDERED 인 주문은 멱등 no-op (save/metric 없음)")
    void alreadyBackordered_isNoOp() {
        Order order = order(OrderStatus.BACKORDERED);
        given(orderRepository.findByIdAcrossTenants(order.getOrderId())).willReturn(Optional.of(order));

        service.markBackordered(order.getOrderId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.BACKORDERED);
        verify(orderRepository, never()).save(any());
        verify(orderMetrics, never()).recordOrderBackordered();
        verify(orderMetrics, never()).recordStatusTransition(any(), any());
    }

    @Test
    @DisplayName("이미 CONFIRMED 인 주문에 늦은 reservation-failed 는 no-op (save/metric 없음)")
    void alreadyConfirmed_isNoOp() {
        Order order = order(OrderStatus.CONFIRMED);
        given(orderRepository.findByIdAcrossTenants(order.getOrderId())).willReturn(Optional.of(order));

        service.markBackordered(order.getOrderId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository, never()).save(any());
        verify(orderMetrics, never()).recordOrderBackordered();
    }

    @Test
    @DisplayName("존재하지 않는 주문은 warn 후 skip (save/metric 없음)")
    void unknownOrder_skips() {
        given(orderRepository.findByIdAcrossTenants("nope")).willReturn(Optional.empty());

        service.markBackordered("nope");

        verify(orderRepository, never()).save(any());
        verify(orderMetrics, never()).recordOrderBackordered();
    }
}
