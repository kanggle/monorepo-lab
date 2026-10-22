package com.example.order.application.service;

import com.example.order.application.dto.AdminOrderStatusChangeResult;
import com.example.order.application.event.OrderCancelledEvent;
import com.example.order.application.port.OrderEventPublisher;
import com.example.order.application.port.OrderMetricsPort;
import com.example.order.domain.exception.InvalidOrderException;
import com.example.order.domain.exception.OrderCannotBeCancelledException;
import com.example.order.domain.exception.OrderNotFoundException;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderItem;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.model.ShippingAddress;
import com.example.order.domain.repository.OrderRepository;
import com.example.order.application.exception.InvalidOrderStatusException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminOrderStatusService 단위 테스트")
class AdminOrderStatusServiceTest {

    /**
     * Wired by hand around a REAL {@link OperatorOrderCancellationService} over the same mocks,
     * so the cancel assertions observe the event / metric the admin path actually produces
     * (TASK-BE-646) rather than an interaction with a stubbed delegate.
     */
    private AdminOrderStatusService adminOrderStatusService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private Clock clock;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @Mock
    private OrderMetricsPort orderMetrics;

    private static final Instant FIXED_NOW = Instant.parse("2026-03-25T10:00:00Z");

    private static final ShippingAddress ADDRESS = ShippingAddress.reconstitute(
            "홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"
    );

    private static final List<OrderItem> ITEMS = List.of(
            OrderItem.reconstitute("item-1", "prod-1", "var-1", "노트북", "블랙", 1, 1_000_000L)
    );

    @BeforeEach
    void setUp() {
        adminOrderStatusService = new AdminOrderStatusService(orderRepository, clock,
                new OperatorOrderCancellationService(orderRepository, orderEventPublisher, orderMetrics, clock));
    }

    private Order createOrderWithStatus(String orderId, OrderStatus status) {
        return Order.reconstitute(
                orderId, "user-1", ITEMS,
                status, 1_000_000L, ADDRESS,
                FIXED_NOW, FIXED_NOW,
                null, null, null, 0, null, 1L
        );
    }

    @Test
    @DisplayName("PENDING 상태 주문을 CONFIRMED로 변경하면 성공하고 변경된 DTO를 반환한다")
    void changeStatus_pendingToConfirmed_successReturnsUpdatedOrder() {
        String orderId = "order-001";
        Order order = createOrderWithStatus(orderId, OrderStatus.PENDING);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        AdminOrderStatusChangeResult result = adminOrderStatusService.changeStatus(orderId, "CONFIRMED");

        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(result.orderId()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("SHIPPED는 운영자가 직접 지정할 수 없고 IllegalArgumentException으로 거부된다 (배송 이벤트 구동 — ADR-MONO-022 §D7)")
    void changeStatus_targetStatusIsShipped_throwsIllegalArgumentException() {
        String orderId = "order-002";
        Order order = createOrderWithStatus(orderId, OrderStatus.CONFIRMED);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> adminOrderStatusService.changeStatus(orderId, "SHIPPED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported target status: SHIPPED");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("DELIVERED는 운영자가 직접 지정할 수 없고 IllegalArgumentException으로 거부된다 (배송 이벤트 구동 — ADR-MONO-022 §D7)")
    void changeStatus_targetStatusIsDelivered_throwsIllegalArgumentException() {
        String orderId = "order-003";
        Order order = createOrderWithStatus(orderId, OrderStatus.SHIPPED);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> adminOrderStatusService.changeStatus(orderId, "DELIVERED"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported target status: DELIVERED");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("PENDING 상태 주문을 CANCELLED로 변경하면 성공하고 변경된 DTO를 반환한다")
    void changeStatus_pendingToCancelled_successReturnsUpdatedOrder() {
        String orderId = "order-004";
        Order order = createOrderWithStatus(orderId, OrderStatus.PENDING);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        AdminOrderStatusChangeResult result = adminOrderStatusService.changeStatus(orderId, "CANCELLED");

        assertThat(result.status()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("CONFIRMED 상태 주문을 CANCELLED로 변경하면 성공하고 변경된 DTO를 반환한다")
    void changeStatus_confirmedToCancelled_successReturnsUpdatedOrder() {
        String orderId = "order-005";
        Order order = createOrderWithStatus(orderId, OrderStatus.CONFIRMED);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        AdminOrderStatusChangeResult result = adminOrderStatusService.changeStatus(orderId, "CANCELLED");

        assertThat(result.status()).isEqualTo("CANCELLED");
    }

    @Test
    @DisplayName("존재하지 않는 orderId로 상태 변경 요청 시 OrderNotFoundException이 발생한다")
    void changeStatus_orderNotFound_throwsOrderNotFoundException() {
        given(orderRepository.findById("nonexistent")).willReturn(Optional.empty());

        assertThatThrownBy(() -> adminOrderStatusService.changeStatus("nonexistent", "CONFIRMED"))
                .isInstanceOf(OrderNotFoundException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("SHIPPED 상태 주문을 CONFIRMED로 변경 시도하면 InvalidOrderException이 발생한다")
    void changeStatus_shippedToConfirmed_throwsInvalidOrderException() {
        String orderId = "order-007";
        Order order = createOrderWithStatus(orderId, OrderStatus.SHIPPED);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> adminOrderStatusService.changeStatus(orderId, "CONFIRMED"))
                .isInstanceOf(InvalidOrderException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("SHIPPED 상태 주문을 CANCELLED로 변경 시도하면 OrderCannotBeCancelledException이 발생한다")
    void changeStatus_shippedToCancelled_throwsOrderCannotBeCancelledException() {
        String orderId = "order-008";
        Order order = createOrderWithStatus(orderId, OrderStatus.SHIPPED);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> adminOrderStatusService.changeStatus(orderId, "CANCELLED"))
                .isInstanceOf(OrderCannotBeCancelledException.class);

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("targetStatus로 PENDING을 지정하면 IllegalArgumentException이 발생한다")
    void changeStatus_targetStatusIsPending_throwsIllegalArgumentException() {
        String orderId = "order-009";
        Order order = createOrderWithStatus(orderId, OrderStatus.PENDING);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> adminOrderStatusService.changeStatus(orderId, "PENDING"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported target status: PENDING");

        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("상태 변경 성공 후 orderRepository.save()가 호출된다")
    void changeStatus_successfulTransition_saveIsCalled() {
        String orderId = "order-010";
        Order order = createOrderWithStatus(orderId, OrderStatus.PENDING);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        adminOrderStatusService.changeStatus(orderId, "CONFIRMED");

        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("BACKORDERED 주문을 운영자가 CONFIRMED로 변경하면 InvalidOrderException으로 거부되고 상태·저장이 그대로다 (재고 예약 없는 확정 금지 — TASK-BE-645)")
    void changeStatus_backorderedToConfirmed_rejectedWithoutSave() {
        String orderId = "order-011";
        Order order = createOrderWithStatus(orderId, OrderStatus.BACKORDERED);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> adminOrderStatusService.changeStatus(orderId, "CONFIRMED"))
                .isInstanceOf(InvalidOrderException.class)
                .hasMessageContaining("BACKORDERED");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.BACKORDERED);
        assertThat(order.getUpdatedAt()).isEqualTo(FIXED_NOW);
        verify(orderRepository, never()).save(any());
    }

    @Test
    @DisplayName("BACKORDERED 주문을 운영자가 CANCELLED로 변경하면 성공한다 (백오더에서 운영자가 할 수 있는 유일한 전이 — TASK-BE-645)")
    void changeStatus_backorderedToCancelled_success() {
        String orderId = "order-012";
        Order order = createOrderWithStatus(orderId, OrderStatus.BACKORDERED);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        AdminOrderStatusChangeResult result = adminOrderStatusService.changeStatus(orderId, "CANCELLED");

        assertThat(result.status()).isEqualTo("CANCELLED");
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("이미 CONFIRMED인 주문을 운영자가 CONFIRMED로 변경하면 멱등 no-op으로 200 결과를 돌려준다 (기존 동작 유지)")
    void changeStatus_confirmedToConfirmed_idempotentNoOp() {
        String orderId = "order-013";
        Order order = createOrderWithStatus(orderId, OrderStatus.CONFIRMED);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));

        AdminOrderStatusChangeResult result = adminOrderStatusService.changeStatus(orderId, "CONFIRMED");

        assertThat(result.status()).isEqualTo("CONFIRMED");
        assertThat(order.getUpdatedAt()).isEqualTo(FIXED_NOW);
    }

    @Test
    @DisplayName("raw status가 잘못된 enum 값이거나 null이면 InvalidOrderStatusException이 발생한다")
    void changeStatus_throwsInvalidOrderStatusExceptionWhenRawStatusIsInvalid() {
        assertThatThrownBy(() -> adminOrderStatusService.changeStatus("order-x", "FOO"))
                .isInstanceOf(InvalidOrderStatusException.class);

        assertThatThrownBy(() -> adminOrderStatusService.changeStatus("order-x", null))
                .isInstanceOf(InvalidOrderStatusException.class);

        verify(orderRepository, never()).save(any());
    }

    // ─── TASK-BE-646: 관리자 취소 = 운영자 취소 (OrderCancelled → 환불 · 예약 해제 · 쿠폰 복원) ───

    @ParameterizedTest(name = "{0} → CANCELLED")
    @EnumSource(value = OrderStatus.class, names = {"PENDING", "CONFIRMED", "BACKORDERED"})
    @DisplayName("관리자 취소는 OrderCancelled(cancelReason=OPERATOR)를 정확히 1회 발행하고 운영자 취소 지표를 남긴다 (TASK-BE-646)")
    void changeStatus_cancel_publishesOrderCancelledOnceAsOperator(OrderStatus from) {
        String orderId = "order-646-" + from.name().toLowerCase();
        Order order = createOrderWithStatus(orderId, from);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        AdminOrderStatusChangeResult result = adminOrderStatusService.changeStatus(orderId, "CANCELLED");

        assertThat(result.orderId()).isEqualTo(orderId);
        assertThat(result.status()).isEqualTo("CANCELLED");
        verify(orderRepository, times(1)).save(order);

        ArgumentCaptor<OrderCancelledEvent> captor = ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(orderEventPublisher, times(1)).publishOrderCancelled(captor.capture());
        OrderCancelledEvent published = captor.getValue();
        assertThat(published.eventType()).isEqualTo("OrderCancelled");
        assertThat(published.payload().orderId()).isEqualTo(orderId);
        assertThat(published.payload().userId()).isEqualTo("user-1");
        assertThat(published.payload().cancelReason()).isEqualTo("OPERATOR");
        assertThat(published.payload().cancelledAt()).isEqualTo(FIXED_NOW.toString());

        verify(orderMetrics).recordOrderCancelled("operator");
        verify(orderMetrics).recordStatusTransition(from.name(), "CANCELLED");
    }

    @Test
    @DisplayName("같은 관리자 취소를 두 번 보내면 두 번째는 422(OrderCannotBeCancelledException)이고 OrderCancelled 는 1회뿐이다 (멱등 — TASK-BE-646)")
    void changeStatus_cancelTwice_publishesExactlyOnce() {
        String orderId = "order-646-twice";
        Order order = createOrderWithStatus(orderId, OrderStatus.CONFIRMED);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        adminOrderStatusService.changeStatus(orderId, "CANCELLED");
        assertThatThrownBy(() -> adminOrderStatusService.changeStatus(orderId, "CANCELLED"))
                .isInstanceOf(OrderCannotBeCancelledException.class);

        verify(orderRepository, times(1)).save(any());
        verify(orderEventPublisher, times(1)).publishOrderCancelled(any());
        verify(orderMetrics, times(1)).recordOrderCancelled("operator");
    }

    @Test
    @DisplayName("이미 CANCELLED 인 주문의 관리자 취소는 기존대로 OrderCannotBeCancelledException 이고 저장 · 발행 · 지표가 없다 (TASK-BE-646)")
    void changeStatus_alreadyCancelled_existingBehaviourNoEvent() {
        String orderId = "order-646-cancelled";
        Order order = createOrderWithStatus(orderId, OrderStatus.CANCELLED);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));

        assertThatThrownBy(() -> adminOrderStatusService.changeStatus(orderId, "CANCELLED"))
                .isInstanceOf(OrderCannotBeCancelledException.class);

        verify(orderRepository, never()).save(any());
        verifyNoInteractions(orderEventPublisher, orderMetrics);
    }

    @Test
    @DisplayName("관리자 확정(PENDING → CONFIRMED)은 OrderCancelled 를 내지 않는다 — 취소 갈래만 바뀐다 (TASK-BE-646 · 확정 무발행은 소유자 결정 대기)")
    void changeStatus_confirm_publishesNoOrderCancelled() {
        String orderId = "order-646-confirm";
        Order order = createOrderWithStatus(orderId, OrderStatus.PENDING);
        given(orderRepository.findById(orderId)).willReturn(Optional.of(order));
        given(orderRepository.save(any())).willAnswer(inv -> inv.getArgument(0));
        given(clock.instant()).willReturn(FIXED_NOW);

        adminOrderStatusService.changeStatus(orderId, "CONFIRMED");

        verify(orderEventPublisher, never()).publishOrderCancelled(any());
    }
}
