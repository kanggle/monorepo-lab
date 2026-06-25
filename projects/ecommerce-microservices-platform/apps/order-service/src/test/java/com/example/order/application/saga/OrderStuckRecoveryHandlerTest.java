package com.example.order.application.saga;

import com.example.order.application.event.OrderCancelledEvent;
import com.example.order.application.event.OrderSagaRecoveryExhaustedEvent;
import com.example.order.application.port.OrderEventPublisher;
import com.example.order.application.port.OrderMetricsPort;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.model.ShippingAddress;
import com.example.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OrderStuckRecoveryHandler unit tests")
class OrderStuckRecoveryHandlerTest {

    private static final Instant NOW = Instant.parse("2026-05-11T10:00:00Z");
    private static final Instant PLACED_AT = NOW.minusSeconds(3600);
    private static final int MAX_ATTEMPTS = 5;

    private OrderRepository orderRepository;
    private OrderEventPublisher publisher;
    private OrderMetricsPort metrics;
    private OrderStuckRecoveryHandler handler;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        publisher = mock(OrderEventPublisher.class);
        metrics = mock(OrderMetricsPort.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        handler = new OrderStuckRecoveryHandler(orderRepository, publisher, metrics, clock);
    }

    @Test
    @DisplayName("첫 attempt: count 0→1, status PENDING 유지, recovery.fired metric +1, 이벤트 발행 없음")
    void firstAttempt_bumpsCount_noEvent() {
        Order order = pendingOrder("order-1", 0);
        when(orderRepository.findByIdAcrossTenants("order-1")).thenReturn(Optional.of(order));

        handler.recover("order-1", MAX_ATTEMPTS);

        assertThat(order.getStuckRecoveryAttemptCount()).isEqualTo(1);
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderRepository).save(order);
        verify(metrics).recordStuckDetectorRecoveryFired(OrderStatus.PENDING.name());
        verify(publisher, never()).publishOrderSagaRecoveryExhausted(any());
    }

    @Test
    @DisplayName("cap 도달 (count 4 → 5): auto-cancel CANCELLED(PAYMENT_TIMEOUT) + OrderCancelled + 정보성 exhausted alert + metric")
    void capReached_autoCancelsAndPublishesCancelAndAlert() {
        Order order = pendingOrder("order-1", 4);
        when(orderRepository.findByIdAcrossTenants("order-1")).thenReturn(Optional.of(order));

        handler.recover("order-1", MAX_ATTEMPTS);

        // Primary terminal is now CANCELLED (not STUCK_RECOVERY_FAILED).
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        verify(orderRepository).save(order);
        verify(metrics).recordStuckDetectorExhausted(OrderStatus.PENDING.name());

        // (1) OrderCancelled with cancelReason = PAYMENT_TIMEOUT — drives payment refund/void.
        ArgumentCaptor<OrderCancelledEvent> cancelCaptor =
                ArgumentCaptor.forClass(OrderCancelledEvent.class);
        verify(publisher).publishOrderCancelled(cancelCaptor.capture());
        OrderCancelledEvent.Payload cancelPayload = cancelCaptor.getValue().payload();
        assertThat(cancelPayload.orderId()).isEqualTo("order-1");
        assertThat(cancelPayload.cancelReason()).isEqualTo("PAYMENT_TIMEOUT");

        // (2) Informational OrderSagaRecoveryExhausted retained for operator visibility.
        ArgumentCaptor<OrderSagaRecoveryExhaustedEvent> captor =
                ArgumentCaptor.forClass(OrderSagaRecoveryExhaustedEvent.class);
        verify(publisher).publishOrderSagaRecoveryExhausted(captor.capture());
        OrderSagaRecoveryExhaustedEvent.Payload payload = captor.getValue().payload();
        assertThat(payload.orderId()).isEqualTo("order-1");
        assertThat(payload.attemptCount()).isEqualTo(5);
        assertThat(payload.lastState()).isEqualTo(OrderStatus.PENDING.name());
        assertThat(payload.failureReason()).isEqualTo(OrderStuckRecoveryHandler.FAILURE_REASON);
        assertThat(payload.failureReason()).isEqualTo("order_auto_cancelled_payment_timeout");
    }

    @Test
    @DisplayName("race: 다시 로드했을 때 payment_id != null 이면 skip (no metric / no event)")
    void racePaymentCompleted_isSkipped() {
        Order order = paidOrder("order-1");
        when(orderRepository.findByIdAcrossTenants("order-1")).thenReturn(Optional.of(order));

        handler.recover("order-1", MAX_ATTEMPTS);

        verify(orderRepository, never()).save(any());
        verify(publisher, never()).publishOrderSagaRecoveryExhausted(any());
        verify(metrics, never()).recordStuckDetectorRecoveryFired(any());
        verify(metrics, never()).recordStuckDetectorExhausted(any());
    }

    @Test
    @DisplayName("race: order 가 사라졌을 때 (Optional.empty) 예외 없이 skip")
    void raceOrderVanished_doesNotThrow() {
        when(orderRepository.findByIdAcrossTenants("order-1")).thenReturn(Optional.empty());

        handler.recover("order-1", MAX_ATTEMPTS);

        verify(orderRepository, never()).save(any());
        verify(publisher, never()).publishOrderSagaRecoveryExhausted(any());
    }

    @Test
    @DisplayName("race: status 가 PENDING 이 아닌 경우 (이미 CONFIRMED) skip")
    void raceAlreadyConfirmed_isSkipped() {
        Order order = paidOrder("order-1");
        // confirm 까지 진행된 상태로 만들기
        order.confirm(Clock.fixed(NOW, ZoneOffset.UTC));
        when(orderRepository.findByIdAcrossTenants("order-1")).thenReturn(Optional.of(order));

        handler.recover("order-1", MAX_ATTEMPTS);

        verify(orderRepository, never()).save(any());
        verify(publisher, never()).publishOrderSagaRecoveryExhausted(any());
    }

    private static Order pendingOrder(String orderId, int attemptCount) {
        return Order.reconstitute(
                orderId, "user-1", List.of(),
                OrderStatus.PENDING, 0L,
                new ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시", null),
                PLACED_AT, PLACED_AT,
                null, null, null,
                attemptCount, attemptCount > 0 ? PLACED_AT.plusSeconds(60) : null,
                1L);
    }

    private static Order paidOrder(String orderId) {
        return Order.reconstitute(
                orderId, "user-1", List.of(),
                OrderStatus.PENDING, 0L,
                new ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시", null),
                PLACED_AT, PLACED_AT,
                "pay-1", PLACED_AT.plusSeconds(10), null,
                0, null, 1L);
    }
}
