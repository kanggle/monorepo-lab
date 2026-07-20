package com.example.order.application.service;

import com.example.order.application.dto.PlaceOrderCommand;
import com.example.order.application.dto.PlaceOrderResult;
import com.example.order.application.event.OrderPlacedEvent;
import com.example.order.application.exception.DuplicateOrderPlacementException;
import com.example.order.application.port.OrderEventPublisher;
import com.example.order.application.port.OrderMetricsPort;
import com.example.order.domain.exception.InvalidOrderException;
import com.example.order.domain.model.Order;
import com.example.order.domain.repository.OrderRepository;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;
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
        given(orderRepository.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        PlaceOrderResult result = orderPlacementService.placeOrder(command);

        assertThat(result.orderId()).isNotNull();
        verify(orderRepository).saveAndFlush(any(Order.class));

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
        given(orderRepository.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));
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
        given(orderRepository.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));
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

        verify(orderRepository, never()).saveAndFlush(any());
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

        verify(orderRepository, never()).saveAndFlush(any());
    }

    private static List<PlaceOrderCommand.OrderItemCommand> oneItem() {
        return List.of(new PlaceOrderCommand.OrderItemCommand("p1", "v1", "노트북", "블랙", 1, 1000000L));
    }

    @Test
    @DisplayName("멱등키 재요청(replay): 기존 주문 orderId를 반환하고 새 주문·이벤트·메트릭이 없다 (TASK-BE-430)")
    void placeOrder_idempotentReplay_returnsExistingOrderIdNoSideEffects() {
        Order existing = mock(Order.class);
        given(existing.getOrderId()).willReturn("existing-1");
        given(orderRepository.findByUserIdAndIdempotencyKey("user1", "key-1"))
                .willReturn(Optional.of(existing));
        PlaceOrderCommand command = new PlaceOrderCommand("user1", oneItem(), ADDRESS, "key-1");

        PlaceOrderResult result = orderPlacementService.placeOrder(command);

        assertThat(result.orderId()).isEqualTo("existing-1");
        verify(orderRepository, never()).saveAndFlush(any());
        verify(orderEventPublisher, never()).publishOrderPlaced(any());
        verify(orderMetrics, never()).recordOrderPlaced();
    }

    @Test
    @DisplayName("새 멱등키: 주문을 생성하고 키를 부여해 저장한다 (TASK-BE-430)")
    void placeOrder_newIdempotencyKey_createsOrderWithKey() {
        given(orderRepository.findByUserIdAndIdempotencyKey("user1", "key-2"))
                .willReturn(Optional.empty());
        given(orderRepository.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);
        PlaceOrderCommand command = new PlaceOrderCommand("user1", oneItem(), ADDRESS, "key-2");

        orderPlacementService.placeOrder(command);

        ArgumentCaptor<Order> orderCaptor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).saveAndFlush(orderCaptor.capture());
        assertThat(orderCaptor.getValue().getIdempotencyKey()).isEqualTo("key-2");
        verify(orderEventPublisher).publishOrderPlaced(any());
    }

    /**
     * TASK-BE-541: this test's premise used to be impossible. It stubbed
     * {@code orderRepository.save(...)} to throw, but the real repository never does —
     * {@code Order} has an assigned {@code @Id}, so a plain {@code save()} queues the
     * INSERT until the commit-time flush, long after the service's catch has been passed.
     * The test was green while the production catch was unreachable and the concurrent
     * duplicate actually returned 500.
     *
     * <p>It now stubs {@code saveAndFlush}, which is the method the service calls and the
     * one that genuinely raises the violation inside the try-block. The premise is real.
     * The end-to-end proof that Postgres rejects the second row lives in the integration
     * lane; this unit test only pins the translation and the no-event guarantee.
     */
    @Test
    @DisplayName("동시 동일 멱등키 race: unique 위반 → DuplicateOrderPlacementException, 이벤트 미발행 (TASK-BE-430, 전제 수정 TASK-BE-541)")
    void placeOrder_concurrentDuplicate_throwsAndNoEvent() {
        given(orderRepository.findByUserIdAndIdempotencyKey("user1", "key-3"))
                .willReturn(Optional.empty());
        given(orderRepository.saveAndFlush(any()))
                .willThrow(new DataIntegrityViolationException("uq_orders_idempotency"));
        given(clock.instant()).willReturn(FIXED_NOW);
        PlaceOrderCommand command = new PlaceOrderCommand("user1", oneItem(), ADDRESS, "key-3");

        assertThatThrownBy(() -> orderPlacementService.placeOrder(command))
                .isInstanceOf(DuplicateOrderPlacementException.class);

        verify(orderEventPublisher, never()).publishOrderPlaced(any());
    }

    @Test
    @DisplayName("멱등키 미전송: dedup 조회 없이 기존(비멱등) 동작 (TASK-BE-430 하위호환)")
    void placeOrder_noIdempotencyKey_legacyBehaviorNoDedupLookup() {
        given(orderRepository.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);
        PlaceOrderCommand command = new PlaceOrderCommand("user1", oneItem(), ADDRESS); // 3-arg = null key

        orderPlacementService.placeOrder(command);

        verify(orderRepository, never()).findByUserIdAndIdempotencyKey(any(), any());
        verify(orderEventPublisher).publishOrderPlaced(any());
    }

    @Test
    @DisplayName("주문 생성 시 메트릭이 기록된다")
    void placeOrder_validCommand_recordsMetrics() {
        PlaceOrderCommand command = new PlaceOrderCommand(
                "user1",
                List.of(new PlaceOrderCommand.OrderItemCommand("p1", "v1", "노트북", "블랙", 1, 1000000L)),
                ADDRESS
        );
        given(orderRepository.saveAndFlush(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        orderPlacementService.placeOrder(command);

        verify(orderMetrics).recordOrderPlaced();
        verify(orderMetrics).recordOrderAmount(1000000L);
    }
}
