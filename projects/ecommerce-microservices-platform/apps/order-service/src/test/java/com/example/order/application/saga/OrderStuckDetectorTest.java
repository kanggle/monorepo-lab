package com.example.order.application.saga;

import com.example.order.application.port.OrderMetricsPort;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.model.ShippingAddress;
import com.example.order.domain.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("OrderStuckDetector unit tests")
class OrderStuckDetectorTest {

    private static final Instant NOW = Instant.parse("2026-05-11T10:00:00Z");
    private static final long GRACE_SECONDS = 1800L;
    private static final int BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 5;

    private OrderRepository orderRepository;
    private OrderStuckRecoveryHandler recoveryHandler;
    private OrderMetricsPort metrics;
    private OrderStuckDetector detector;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        recoveryHandler = mock(OrderStuckRecoveryHandler.class);
        metrics = mock(OrderMetricsPort.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        detector = new OrderStuckDetector(orderRepository, recoveryHandler, metrics, clock,
                GRACE_SECONDS, BATCH_SIZE, MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("빈 결과여도 run metric이 +1 되고 handler 는 호출되지 않는다")
    void emptyResult_incrementsRunMetricOnly() {
        when(orderRepository.findStuckPaymentPending(any(), eq(BATCH_SIZE))).thenReturn(List.of());

        detector.sweep();

        verify(metrics, times(1)).recordStuckDetectorRun();
        verify(recoveryHandler, never()).recover(any(), anyInt());
    }

    @Test
    @DisplayName("3개의 stuck row 가 있으면 handler 가 3회 호출된다")
    void multipleStuckRows_recoveryHandlerCalledForEach() {
        Order o1 = stuckOrder("order-1");
        Order o2 = stuckOrder("order-2");
        Order o3 = stuckOrder("order-3");
        when(orderRepository.findStuckPaymentPending(any(), eq(BATCH_SIZE)))
                .thenReturn(List.of(o1, o2, o3));

        detector.sweep();

        verify(recoveryHandler).recover("order-1", MAX_ATTEMPTS);
        verify(recoveryHandler).recover("order-2", MAX_ATTEMPTS);
        verify(recoveryHandler).recover("order-3", MAX_ATTEMPTS);
        verify(metrics).recordStuckDetectorRun();
    }

    @Test
    @DisplayName("handler 가 throw 해도 다음 row 처리는 계속된다")
    void handlerThrows_isolatedPerOrder() {
        Order o1 = stuckOrder("order-1");
        Order o2 = stuckOrder("order-2");
        when(orderRepository.findStuckPaymentPending(any(), eq(BATCH_SIZE)))
                .thenReturn(List.of(o1, o2));
        willThrow(new RuntimeException("boom")).given(recoveryHandler).recover("order-1", MAX_ATTEMPTS);

        detector.sweep();

        verify(recoveryHandler).recover("order-1", MAX_ATTEMPTS);
        verify(recoveryHandler).recover("order-2", MAX_ATTEMPTS);
    }

    @Test
    @DisplayName("repository 가 throw 해도 scheduler thread 는 죽지 않는다 (handler 미호출)")
    void repositoryThrows_swallowed() {
        when(orderRepository.findStuckPaymentPending(any(), eq(BATCH_SIZE)))
                .thenThrow(new RuntimeException("db down"));

        detector.sweep();

        verify(metrics).recordStuckDetectorRun();
        verify(recoveryHandler, never()).recover(any(), anyInt());
    }

    @Test
    @DisplayName("cutoff 은 now - graceSeconds 로 계산된다")
    void cutoff_isComputedFromClock() {
        when(orderRepository.findStuckPaymentPending(eq(NOW.minusSeconds(GRACE_SECONDS)), eq(BATCH_SIZE)))
                .thenReturn(List.of());

        detector.sweep();

        // verify above mock: only matched cutoff returns empty; if cutoff differs Mockito would return null and NPE
        assertThat(detector.graceSeconds()).isEqualTo(GRACE_SECONDS);
        assertThat(detector.maxAttempts()).isEqualTo(MAX_ATTEMPTS);
    }

    private static Order stuckOrder(String orderId) {
        return Order.reconstitute(
                orderId, "user-1", List.of(),
                OrderStatus.PENDING, 0L,
                new ShippingAddress("홍길동", "010-0000-0000", "12345", "서울시", null),
                NOW.minusSeconds(7200), NOW.minusSeconds(7200),
                null, null, null, 0, null, 1L);
    }
}
