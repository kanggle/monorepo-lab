package com.example.order.application.saga;

import com.example.order.application.event.OrderCancelledEvent;
import com.example.order.application.event.OrderSagaRecoveryExhaustedEvent;
import com.example.order.application.port.OrderEventPublisher;
import com.example.order.application.port.OrderMetricsPort;
import com.example.order.domain.model.CancelReason;
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
 *   <li>If {@code attempt + 1 >= maxAttempts}: <b>auto-cancel</b> the order
 *       ({@code PENDING → CANCELLED}, {@link CancelReason#PAYMENT_TIMEOUT}) and
 *       co-commit, in the same {@code REQUIRES_NEW} TX, both (a) an
 *       {@code OrderCancelled} event with {@code cancelReason = PAYMENT_TIMEOUT}
 *       (drives payment-service refund/void + product-service stock release) and
 *       (b) the informational {@code order.alert.saga.recovery.exhausted} event
 *       (operator visibility, retained from the old terminal). Increment the
 *       exhausted metric. ADR-MONO-005 § 2.3 D3 auto-resolving-terminal refinement
 *       (TASK-MONO-306 / TASK-BE-435): the primary terminal is now
 *       {@code CANCELLED(PAYMENT_TIMEOUT)} rather than {@code STUCK_RECOVERY_FAILED}.</li>
 * </ul>
 *
 * <p>Exceptions roll back this handler's TX only (the cancel + both events are
 * all-or-nothing) — the order stays {@code PENDING} and the next sweeper tick
 * retries. {@code STUCK_RECOVERY_FAILED} (R4) is retained in the domain as a
 * defensive fallback, no longer the primary terminal. The detector loop keeps
 * iterating across other stuck orders.
 */
@Slf4j
@Component
public class OrderStuckRecoveryHandler {

    /**
     * Informational failure reason on the retained {@code OrderSagaRecoveryExhausted} alert.
     * Reworded for the auto-resolving terminal (TASK-BE-435): the order is auto-cancelled, not
     * left stuck — the alert is now an operator-visibility signal, not a hand-off.
     */
    static final String FAILURE_REASON =
            "order_auto_cancelled_payment_timeout";

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
        // Tenant-agnostic: the detector sweeps stuck orders globally (operational),
        // so recovery must reach the order regardless of any ambient tenant context.
        // Its immutable tenant_id is preserved through the recovery save.
        Order order = orderRepository.findByIdAcrossTenants(orderId).orElse(null);
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

    /**
     * At-cap terminal (TASK-BE-435, ADR-MONO-005 § 2.3 D3 auto-resolving refinement):
     * auto-cancel the stuck payment-pending order ({@code PENDING → CANCELLED},
     * {@link CancelReason#PAYMENT_TIMEOUT}) instead of the old terminal
     * {@code STUCK_RECOVERY_FAILED}. All three writes (cancel save + {@code OrderCancelled}
     * + informational {@code OrderSagaRecoveryExhausted}) co-commit in this handler's
     * {@code REQUIRES_NEW} TX; on any failure the TX rolls back, the order stays
     * {@code PENDING}, and the next sweeper tick retries (R4 safety — {@code STUCK_RECOVERY_FAILED}
     * remains a defensive fallback in the domain, no longer the primary path).
     */
    private void markExhausted(Order order, int finalAttemptCount,
                               Instant placedAt, Instant lastTransition, Instant now) {
        // Primary terminal: PENDING → CANCELLED(PAYMENT_TIMEOUT). cancel() stamps updatedAt = now
        // (Instant.now(clock) == now under the fixed handler clock).
        order.cancel(CancelReason.PAYMENT_TIMEOUT, clock);
        orderRepository.save(order);

        // (1) OrderCancelled(PAYMENT_TIMEOUT) — drives payment refund/void + stock release.
        eventPublisher.publishOrderCancelled(
                OrderCancelledEvent.of(order.getOrderId(), order.getUserId(),
                        order.getUpdatedAt(), CancelReason.PAYMENT_TIMEOUT, clock));

        // (2) OrderSagaRecoveryExhausted — retained operator-visibility signal (R3), now informational.
        OrderSagaRecoveryExhaustedEvent alert = OrderSagaRecoveryExhaustedEvent.of(
                order.getOrderId(), order.getUserId(),
                OrderStatus.PENDING.name(), finalAttemptCount,
                placedAt, lastTransition, FAILURE_REASON, clock);
        eventPublisher.publishOrderSagaRecoveryExhausted(alert);

        metrics.recordStuckDetectorExhausted(OrderStatus.PENDING.name());
        log.warn("order_auto_cancelled_payment_timeout orderId={} attempts={}",
                order.getOrderId(), finalAttemptCount);
    }
}
