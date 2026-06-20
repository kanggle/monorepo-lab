package com.example.order.application.service;

import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-order forward-confirm handler for the stale-paid sweep (TASK-BE-412).
 *
 * <p>Lives on a separate bean from {@link StalePaidOrderConfirmService} so the
 * {@code REQUIRES_NEW} {@code @Transactional} call is intercepted by the Spring
 * AOP proxy. Self-invocation from the sweep service would bypass the proxy and
 * silently downgrade to the calling thread's TX (the private-{@code @Transactional}-
 * on-{@code this} regression captured in memory {@code feedback_refactor_code_baseline_it.md}).
 * Same cross-bean split as BE-138 {@link com.example.order.application.saga.OrderStuckDetector}
 * → {@link com.example.order.application.saga.OrderStuckRecoveryHandler}.
 *
 * <p>Each call runs in its own transaction so a per-order failure (optimistic-lock
 * conflict, etc.) rolls back only that order and the sweep loop keeps iterating.
 * The order is re-loaded and its status re-checked inside this TX: an order that
 * raced out of {@code PENDING} (already {@code CONFIRMED}, or {@code CANCELLED} via
 * user cancel) between the sweep's select and now is {@code SKIPPED}, never
 * force-confirmed.
 *
 * <p>When the order is still {@code PENDING}, confirmation is delegated to the
 * normal saga path {@link OrderConfirmationService#confirmOrder(String)} — the
 * SAME re-load + {@code Order.confirm(clock)} + save + metrics + outbox-publish of
 * {@code OrderConfirmed} the {@code StockChanged} consumer uses — so a batch-recovered
 * confirm is byte-for-byte identical to a normal one (downstream fulfillment fires).
 * The delegated call joins this REQUIRES_NEW transaction (it is {@code REQUIRED}),
 * so the status transition and the outbox row co-commit atomically.
 */
@Slf4j
@Component
public class StalePaidOrderConfirmHandler {

    /** Outcome of a single per-order confirm attempt. */
    public enum Outcome {
        /** Order transitioned {@code PENDING → CONFIRMED}; {@code OrderConfirmed} emitted. */
        CONFIRMED,
        /** Order was already {@code CONFIRMED} or had raced out of {@code PENDING}; no-op, no event. */
        SKIPPED
    }

    private final OrderRepository orderRepository;
    private final OrderConfirmationService confirmationService;

    public StalePaidOrderConfirmHandler(OrderRepository orderRepository,
                                        OrderConfirmationService confirmationService) {
        this.orderRepository = orderRepository;
        this.confirmationService = confirmationService;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Outcome confirmIfStillPending(String orderId) {
        // Tenant-agnostic: the sweep matches paid-unconfirmed orders globally
        // (operational), so recovery must reach the order regardless of any ambient
        // tenant context. The order's immutable tenant_id is preserved across the save.
        Order order = orderRepository.findByIdAcrossTenants(orderId).orElse(null);
        if (order == null) {
            log.warn("stale_paid_confirm_order_vanished orderId={}", orderId);
            return Outcome.SKIPPED;
        }
        // Re-check under a fresh load: the order may have advanced (CONFIRMED by the
        // normal saga, or CANCELLED by the user) between the sweep's select and now.
        if (order.getStatus() != OrderStatus.PENDING) {
            log.debug("stale_paid_confirm_skip_advanced orderId={} status={}",
                    orderId, order.getStatus());
            return Outcome.SKIPPED;
        }
        // Delegate to the normal confirm path (joins this REQUIRES_NEW TX). It re-loads,
        // calls Order.confirm(clock) (false → already CONFIRMED → no event), saves,
        // records metrics, and publishes OrderConfirmed via the transactional outbox.
        confirmationService.confirmOrder(orderId);
        log.info("stale_paid_confirm_confirmed orderId={}", orderId);
        return Outcome.CONFIRMED;
    }
}
