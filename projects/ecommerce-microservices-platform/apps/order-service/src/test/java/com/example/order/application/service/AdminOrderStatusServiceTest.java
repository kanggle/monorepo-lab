package com.example.order.application.service;

import com.example.order.application.dto.AdminOrderStatusChangeResult;
import com.example.order.domain.exception.InvalidOrderException;
import com.example.order.domain.exception.OrderCannotBeCancelledException;
import com.example.order.domain.exception.OrderNotFoundException;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderItem;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.model.ShippingAddress;
import com.example.order.domain.repository.OrderRepository;
import com.example.order.application.exception.InvalidOrderStatusException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
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
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminOrderStatusService 단위 테스트")
class AdminOrderStatusServiceTest {

    @InjectMocks
    private AdminOrderStatusService adminOrderStatusService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private Clock clock;

    private static final Instant FIXED_NOW = Instant.parse("2026-03-25T10:00:00Z");

    private static final ShippingAddress ADDRESS = ShippingAddress.reconstitute(
            "홍길동", "010-1234-5678", "12345", "서울시 강남구", "101호"
    );

    private static final List<OrderItem> ITEMS = List.of(
            OrderItem.reconstitute("item-1", "prod-1", "var-1", "노트북", "블랙", 1, 1_000_000L)
    );

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
    @DisplayName("raw status가 잘못된 enum 값이거나 null이면 InvalidOrderStatusException이 발생한다")
    void changeStatus_throwsInvalidOrderStatusExceptionWhenRawStatusIsInvalid() {
        assertThatThrownBy(() -> adminOrderStatusService.changeStatus("order-x", "FOO"))
                .isInstanceOf(InvalidOrderStatusException.class);

        assertThatThrownBy(() -> adminOrderStatusService.changeStatus("order-x", null))
                .isInstanceOf(InvalidOrderStatusException.class);

        verify(orderRepository, never()).save(any());
    }
}
