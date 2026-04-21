package com.example.order.application.service;

import com.example.order.application.port.OrderMetricsPort;
import com.example.order.domain.exception.OrderNotFoundException;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderConfirmationService 단위 테스트")
class OrderConfirmationServiceTest {

    @InjectMocks
    private OrderConfirmationService orderConfirmationService;

    @Mock
    private OrderRepository orderRepository;

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
    @DisplayName("PENDING 주문 확정 시 CONFIRMED 상태로 변경되고 메트릭이 기록된다")
    void confirmOrder_pendingOrder_becomesConfirmedAndRecordsMetrics() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        given(orderRepository.findById(order.getOrderId())).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        orderConfirmationService.confirmOrder(order.getOrderId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository).save(order);
        verify(orderMetrics).recordOrderConfirmed();
        verify(orderMetrics).recordStatusTransition("PENDING", "CONFIRMED");
    }

    @Test
    @DisplayName("이미 CONFIRMED인 주문 확정 시 메트릭이 기록되지 않는다")
    void confirmOrder_alreadyConfirmed_doesNotRecordMetrics() {
        Order order = Order.create("user1",
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
        order.confirm(FIXED_CLOCK);
        given(orderRepository.findById(order.getOrderId())).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        orderConfirmationService.confirmOrder(order.getOrderId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        verify(orderRepository).save(order);
        verify(orderMetrics, never()).recordOrderConfirmed();
        verify(orderMetrics, never()).recordStatusTransition(any(), any());
    }

    @Test
    @DisplayName("존재하지 않는 orderId 확정 시 OrderNotFoundException이 발생한다")
    void confirmOrder_notFound_throwsOrderNotFoundException() {
        given(orderRepository.findById("nonexistent")).willReturn(Optional.empty());

        assertThatThrownBy(() -> orderConfirmationService.confirmOrder("nonexistent"))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
