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
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserWithdrawalOrderService 단위 테스트")
class UserWithdrawalOrderServiceTest {

    @InjectMocks
    private UserWithdrawalOrderService userWithdrawalOrderService;

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

    private Order createOrder(String userId) {
        return Order.create(userId,
                List.of(new Order.OrderItemData("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS, FIXED_CLOCK);
    }

    @Test
    @DisplayName("활성 주문이 있으면 모두 취소하고 각각 OrderCancelled 이벤트를 발행한다")
    void cancelOrdersForWithdrawnUser_activeOrders_cancelsAllAndPublishesEvents() {
        String userId = "user-1";
        Order order1 = createOrder(userId);
        Order order2 = createOrder(userId);

        given(orderRepository.findByUserIdAndStatusIn(eq(userId), any())).willReturn(List.of(order1, order2));
        given(orderRepository.saveAll(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        userWithdrawalOrderService.cancelOrdersForWithdrawnUser(userId);

        assertThat(order1.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order2.getStatus()).isEqualTo(OrderStatus.CANCELLED);

        verify(orderRepository, times(1)).saveAll(any());
        verify(orderRepository, never()).save(any());

        ArgumentCaptor<OrderCancelledEvent> eventCaptor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(orderEventPublisher, times(2)).publishOrderCancelled(eventCaptor.capture());

        List<OrderCancelledEvent> events = eventCaptor.getAllValues();
        assertThat(events).hasSize(2);
        assertThat(events.get(0).payload().orderId()).isEqualTo(order1.getOrderId());
        assertThat(events.get(1).payload().orderId()).isEqualTo(order2.getOrderId());
    }

    @Test
    @DisplayName("활성 주문이 없으면 취소하지 않고 정상 완료한다")
    void cancelOrdersForWithdrawnUser_noActiveOrders_doesNothing() {
        String userId = "user-1";
        given(orderRepository.findByUserIdAndStatusIn(eq(userId), any())).willReturn(Collections.emptyList());

        userWithdrawalOrderService.cancelOrdersForWithdrawnUser(userId);

        verify(orderRepository, never()).saveAll(any());
        verify(orderEventPublisher, never()).publishOrderCancelled(any(OrderCancelledEvent.class));
    }

    @Test
    @DisplayName("동일 이벤트를 2회 처리해도 결과가 동일하다 (멱등성)")
    void cancelOrdersForWithdrawnUser_calledTwice_idempotent() {
        String userId = "user-1";
        Order order = createOrder(userId);

        given(orderRepository.findByUserIdAndStatusIn(eq(userId), any()))
                .willReturn(List.of(order))
                .willReturn(Collections.emptyList());
        given(orderRepository.saveAll(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        userWithdrawalOrderService.cancelOrdersForWithdrawnUser(userId);
        userWithdrawalOrderService.cancelOrdersForWithdrawnUser(userId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository, times(1)).saveAll(any());
        verify(orderEventPublisher, times(1)).publishOrderCancelled(any(OrderCancelledEvent.class));
    }

    @Test
    @DisplayName("각 취소 건에 대해 메트릭이 기록된다")
    void cancelOrdersForWithdrawnUser_recordsMetrics() {
        String userId = "user-1";
        Order order = createOrder(userId);

        given(orderRepository.findByUserIdAndStatusIn(eq(userId), any())).willReturn(List.of(order));
        given(orderRepository.saveAll(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        userWithdrawalOrderService.cancelOrdersForWithdrawnUser(userId);

        verify(orderMetrics).recordOrderCancelled("user_withdrawn");
        verify(orderMetrics).recordStatusTransition("PENDING", "CANCELLED");
    }

    @SuppressWarnings("unchecked")
    @Test
    @DisplayName("다수 주문이 saveAll()로 한 번에 배치 저장된다")
    void cancelOrdersForWithdrawnUser_multipleOrders_batchSaved() {
        String userId = "user-1";
        Order order1 = createOrder(userId);
        Order order2 = createOrder(userId);
        Order order3 = createOrder(userId);

        given(orderRepository.findByUserIdAndStatusIn(eq(userId), any()))
                .willReturn(List.of(order1, order2, order3));
        given(orderRepository.saveAll(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        userWithdrawalOrderService.cancelOrdersForWithdrawnUser(userId);

        ArgumentCaptor<List<Order>> saveCaptor = ArgumentCaptor.forClass(List.class);
        verify(orderRepository, times(1)).saveAll(saveCaptor.capture());

        List<Order> savedOrders = saveCaptor.getValue();
        assertThat(savedOrders).hasSize(3);
        assertThat(savedOrders).allMatch(o -> o.getStatus() == OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("이벤트는 배치 저장 완료 후 일괄 발행된다")
    void cancelOrdersForWithdrawnUser_eventsPublishedAfterBatchSave() {
        String userId = "user-1";
        Order order1 = createOrder(userId);
        Order order2 = createOrder(userId);

        given(orderRepository.findByUserIdAndStatusIn(eq(userId), any()))
                .willReturn(List.of(order1, order2));
        given(orderRepository.saveAll(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        userWithdrawalOrderService.cancelOrdersForWithdrawnUser(userId);

        var inOrder = inOrder(orderRepository, orderEventPublisher);
        inOrder.verify(orderRepository).saveAll(any());
        inOrder.verify(orderEventPublisher, times(2)).publishOrderCancelled(any());
    }
}
