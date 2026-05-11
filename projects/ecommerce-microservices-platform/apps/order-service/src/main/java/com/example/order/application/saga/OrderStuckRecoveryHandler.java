package com.example.order.application.saga;

import com.example.order.application.event.OrderSagaRecoveryExhaustedEvent;
import com.example.order.application.port.OrderEventPublisher;
import com.example.order.application.port.OrderMetricsPort;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;

/**
 * Per-order stuck-recovery handler (TASK-BE-138, ADR-MONO-005 Category A).
 *
 * <p>Lives on a separate bean from {@link OrderStuckDetector} so the
 * {@code REQUIRES_NEW} {@code @Transactional} call is intercepted by the
 * Spring AOP proxy. Self-invocation from the detector would bypass the proxy
 * and silently downgrade to the calling thread's TX (the regression captured
 * in memory {@code feedback_refactor_code_baseline_it.md}). Mirrors the
 * wms {@code SagaRecoveryHandler} (TASK-BE-050) split.
 *
 * <p>Each call runs in its own transaction:
 * <ul>
 *   <li>If {@code attempt + 1 < maxAttempts}: bump the order's
 *       {@code stuck_recovery_attempt_count}, save the row, increment the
 *       recovery-fired metric. The order's {@code PENDING} status is unchanged
 *       so the next sweeper tick will see it again.</li>
 *   <li>If {@code attempt + 1 >= maxAttempts}: transition the order to
 *       {@link OrderStatus#STUCK_RECOVERY_FAILED}, emit
 *       {@code order.alert.saga.recovery.exhausted} via the standard outbox
 *       writer, increment the exhausted metric. The two writes commit
 *       atomically (T3 transactional outbox).</li>
 * </ul>
 *
 * <p>Exceptions roll back this handler's TX only — the detector loop keeps
 * iterating across other stuck orders.
 */
@Slf4j
@Component
public class OrderStuckRecoveryHandler {

    static final String FAILURE_REASON =
            "order_stuck_payment_pending_attempts_exhausted";

    private final OrderRepository orderRepository;
    private final OrderEventPublisher eventPublisher;
    private final OrderMetricsPort metrics;
    private final Clock clock;

    public OrderStuckRecoveryHandler(OrderRepository orderRepository,
                                     OrderEventPublisher eventPublisher,
                                     OrderMetricsPort metrics,
                                     Clock clock) {
        this.orderRepository = orderRepository;
        this.eventPublisher = eventPublisher;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recover(String orderId, int maxAttempts) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("order_stuck_recovery_order_vanished orderId={}", orderId);
            return;
        }
        // Re-check state under fresh load — the order may have advanced
        // between the detector's findStuckPaymentPending() and now.
        if (order.getStatus() != OrderStatus.PENDING || order.getPaymentId() != null) {
            log.debug("order_stuck_recovery_skip_advanced orderId={} status={} paymentId={}",
                    orderId, order.getStatus(), order.getPaymentId());
            return;
        }
        Instant now = Instant.now(clock);
        int nextAttempt = order.getStuckRecoveryAttemptCount() + 1;
        Instant placedAt = order.getCreatedAt();
        Instant lastTransition = order.getStuckRecoveryAt() != null
                ? order.getStuckRecoveryAt()
                : order.getCreatedAt();

        if (nextAttempt >= maxAttempts) {
            markExhausted(order, nextAttempt, placedAt, lastTransition, now);
            return;
        }

        order.recordStuckRecoveryAttempt(now);
        orderRepository.save(order);
        metrics.recordStuckDetectorRecoveryFired(OrderStatus.PENDING.name());
        log.info("order_stuck_recovery_attempt orderId={} attempt={}",
                orderId, nextAttempt);
    }

    private void markExhausted(Order order, int finalAttemptCount,
                               Instant placedAt, Instant lastTransition, Instant now) {
        order.markStuckRecoveryFailed(now);
        orderRepository.save(order);

        OrderSagaRecoveryExhaustedEvent alert = OrderSagaRecoveryExhaustedEvent.of(
                order.getOrderId(), order.getUserId(),
                OrderStatus.PENDING.name(), finalAttemptCount,
                placedAt, lastTransition, FAILURE_REASON, clock);
        eventPublisher.publishOrderSagaRecoveryExhausted(alert);

        metrics.recordStuckDetectorExhausted(OrderStatus.PENDING.name());
        log.warn("order_stuck_recovery_exhausted orderId={} attempts={}",
                order.getOrderId(), finalAttemptCount);
    }
}
