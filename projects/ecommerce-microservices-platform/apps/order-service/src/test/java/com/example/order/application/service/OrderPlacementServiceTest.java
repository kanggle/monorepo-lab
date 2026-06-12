package com.example.order.application.service;

import com.example.order.application.dto.PlaceOrderCommand;
import com.example.order.application.dto.PlaceOrderResult;
import com.example.order.application.event.OrderPlacedEvent;
import com.example.order.application.port.OrderEventPublisher;
import com.example.order.application.port.OrderMetricsPort;
import com.example.order.domain.exception.InvalidOrderException;
import com.example.order.domain.model.Order;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderPlacementService 단위 테스트")
class OrderPlacementServiceTest {

    @InjectMocks
    private OrderPlacementService orderPlacementService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @Mock
    private OrderMetricsPort orderMetrics;

    @Mock
    private Clock clock;

    private static final Instant FIXED_NOW = Instant.parse("2026-03-25T10:00:00Z");

    private static final PlaceOrderCommand.ShippingAddressCommand ADDRESS =
            new PlaceOrderCommand.ShippingAddressCommand("홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호");

    @Test
    @DisplayName("정상 주문 생성 시 orderId를 반환하고 OrderEventPublisher로 이벤트를 발행한다")
    void placeOrder_validCommand_returnsOrderIdAndPublishesEvent() {
        PlaceOrderCommand command = new PlaceOrderCommand(
                "user1",
                List.of(new PlaceOrderCommand.OrderItemCommand("p1", "v1", "노트북", "블랙", 1, 1000000L)),
                ADDRESS
        );
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        PlaceOrderResult result = orderPlacementService.placeOrder(command);

        assertThat(result.orderId()).isNotNull();
        verify(orderRepository).save(any(Order.class));

        ArgumentCaptor<OrderPlacedEvent> eventCaptor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(orderEventPublisher).publishOrderPlaced(eventCaptor.capture());
        assertThat(eventCaptor.getValue().eventType()).isEqualTo("OrderPlaced");
        assertThat(eventCaptor.getValue().payload().userId()).isEqualTo("user1");
    }

    @Test
    @DisplayName("OrderPlaced 이벤트 라인이 각 셀러를 담는다 (다중 셀러 귀속)")
    void placeOrder_multiSeller_eventItemsCarrySellerId() {
        PlaceOrderCommand command = new PlaceOrderCommand(
                "user1",
                List.of(
                        new PlaceOrderCommand.OrderItemCommand("p1", "v1", "노트북", "블랙", 1, 1000000L, "seller-a1"),
                        new PlaceOrderCommand.OrderItemCommand("p2", "v2", "마우스", null, 2, 50000L, "seller-a2")
                ),
                ADDRESS
        );
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        orderPlacementService.placeOrder(command);

        ArgumentCaptor<OrderPlacedEvent> eventCaptor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(orderEventPublisher).publishOrderPlaced(eventCaptor.capture());
        assertThat(eventCaptor.getValue().payload().items())
                .extracting(OrderPlacedEvent.Item::sellerId)
                .containsExactly("seller-a1", "seller-a2");
    }

    @Test
    @DisplayName("seller 미지정 라인은 default seller 로 이벤트에 실린다 (degrade)")
    void placeOrder_absentSeller_eventItemDefaults() {
        PlaceOrderCommand command = new PlaceOrderCommand(
                "user1",
                List.of(new PlaceOrderCommand.OrderItemCommand("p1", "v1", "노트북", "블랙", 1, 1000000L)),
                ADDRESS
        );
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        orderPlacementService.placeOrder(command);

        ArgumentCaptor<OrderPlacedEvent> eventCaptor = ArgumentCaptor.forClass(OrderPlacedEvent.class);
        verify(orderEventPublisher).publishOrderPlaced(eventCaptor.capture());
        assertThat(eventCaptor.getValue().payload().items().get(0).sellerId()).isEqualTo("default");
    }

    @Test
    @DisplayName("items가 비어있으면 InvalidOrderException이 발생한다")
    void placeOrder_emptyItems_throwsInvalidOrderException() {
        PlaceOrderCommand command = new PlaceOrderCommand("user1", List.of(), ADDRESS);

        assertThatThrownBy(() -> orderPlacementService.placeOrder(command))
                .isInstanceOf(InvalidOrderException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("userId가 없으면 InvalidOrderException이 발생한다")
    void placeOrder_blankUserId_throwsInvalidOrderException() {
        PlaceOrderCommand command = new PlaceOrderCommand(
                "",
                List.of(new PlaceOrderCommand.OrderItemCommand("p1", "v1", "노트북", null, 1, 1000L)),
                ADDRESS
        );

        assertThatThrownBy(() -> orderPlacementService.placeOrder(command))
                .isInstanceOf(InvalidOrderException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("주문 생성 시 메트릭이 기록된다")
    void placeOrder_validCommand_recordsMetrics() {
        PlaceOrderCommand command = new PlaceOrderCommand(
                "user1",
                List.of(new PlaceOrderCommand.OrderItemCommand("p1", "v1", "노트북", "블랙", 1, 1000000L)),
                ADDRESS
        );
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        orderPlacementService.placeOrder(command);

        verify(orderMetrics).recordOrderPlaced();
        verify(orderMetrics).recordOrderAmount(1000000L);
    }
}
