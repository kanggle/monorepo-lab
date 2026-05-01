package com.wms.outbound.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.outbound.domain.exception.OrderAlreadyShippedException;
import com.wms.outbound.domain.exception.StateTransitionInvalidException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OrderTest {

    private static final String ACTOR = "user-1";
    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");

    @Test
    void receivedToPickingTransitionsCleanly() {
        Order order = newReceivedOrder();

        order.startPicking(T0, ACTOR);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PICKING);
    }

    @Test
    void fullHappyPathTraversesEveryState() {
        Order order = newReceivedOrder();

        order.startPicking(T0, ACTOR);
        order.completePicking(T0, ACTOR);
        order.startPacking(T0, ACTOR);
        order.completePacking(T0, ACTOR);
        order.confirmShipping(T0, ACTOR);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void invalidTransitionThrows() {
        Order order = newReceivedOrder();
        // RECEIVED → completePicking is illegal.
        assertThatThrownBy(() -> order.completePicking(T0, ACTOR))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void cancelFromAnyAllowedStateTransitionsToCancelled() {
        for (OrderStatus from : List.of(
                OrderStatus.RECEIVED, OrderStatus.PICKING, OrderStatus.PICKED,
                OrderStatus.PACKING, OrderStatus.PACKED)) {
            Order order = orderInState(from);
            order.cancel("ops decision", T0, ACTOR);
            assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        }
    }

    @Test
    void cancelFromShippedThrowsOrderAlreadyShipped() {
        Order order = orderInState(OrderStatus.SHIPPED);
        assertThatThrownBy(() -> order.cancel("late", T0, ACTOR))
                .isInstanceOf(OrderAlreadyShippedException.class);
    }

    @Test
    void cancelFromCancelledThrowsStateTransitionInvalid() {
        Order order = orderInState(OrderStatus.CANCELLED);
        assertThatThrownBy(() -> order.cancel("again", T0, ACTOR))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void linesAreImmutableAfterStartPicking() {
        Order order = newReceivedOrder();
        assertThat(order.linesAreMutable()).isTrue();
        order.startPicking(T0, ACTOR);
        assertThat(order.linesAreMutable()).isFalse();
    }

    private static Order newReceivedOrder() {
        return orderInState(OrderStatus.RECEIVED);
    }

    private static Order orderInState(OrderStatus status) {
        UUID orderId = UUID.randomUUID();
        OrderLine line = new OrderLine(UUID.randomUUID(), orderId, 1,
                UUID.randomUUID(), null, 10);
        return new Order(orderId, "ORD-1", OrderSource.MANUAL,
                UUID.randomUUID(), UUID.randomUUID(),
                null, "notes",
                status, 0L,
                T0, ACTOR, T0, ACTOR,
                List.of(line));
    }
}
