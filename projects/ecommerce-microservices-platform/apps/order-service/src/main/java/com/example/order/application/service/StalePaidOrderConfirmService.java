package com.example.order.application.service;

import com.example.order.application.dto.ConfirmPaidStaleResult;
import com.example.order.domain.model.Order;
import com.example.order.domain.repository.OrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stale-paid forward-confirm sweep (TASK-BE-412).
 *
 * <p>Recovers paid-but-unconfirmed orders: a row whose payment completed
 * ({@code payment_id IS NOT NULL}) but whose confirmation event
 * ({@code product.product.stock-changed} reason {@code ORDER_RESERVED}) was lost,
 * leaving it stuck in {@code PENDING}. The customer already paid, so the correct
 * recovery is to <b>forward-confirm</b> (never cancel) — replaying exactly the
 * normal saga's confirm step so downstream fulfillment fires.
 *
 * <p>Disjoint from BE-138's {@code OrderStuckDetector} on {@code payment_id}: that
 * owns the {@code payment_id IS NULL} bucket (payment never completed → escalate to
 * {@code STUCK_RECOVERY_FAILED}); this owns the {@code payment_id IS NOT NULL} bucket.
 *
 * <p>This service is intentionally NOT {@code @Transactional}: the predicate query is
 * a plain read and each order is confirmed in its own {@code REQUIRES_NEW} transaction
 * via {@link StalePaidOrderConfirmHandler} (separate AOP-proxied bean — the BE-138
 * cross-bean split). A per-order failure is isolated to that order's transaction;
 * the loop keeps iterating, the failed order is excluded from {@code confirmed} and
 * left for the next tick, and the call still returns a partial tally.
 */
@Slf4j
@Service
public class StalePaidOrderConfirmService {

    private final OrderRepository orderRepository;
    private final StalePaidOrderConfirmHandler handler;
    private final Clock clock;

    public StalePaidOrderConfirmService(OrderRepository orderRepository,
                                        StalePaidOrderConfirmHandler handler,
                                        Clock clock) {
        this.orderRepository = orderRepository;
        this.handler = handler;
        this.clock = clock;
    }

    /**
     * Sweeps the paid-unconfirmed bucket older than {@code olderThanMinutes}, capped at
     * {@code limit}, forward-confirming each matched order through the normal saga path.
     *
     * @param olderThanMinutes orders younger than this are not swept (the normal saga
     *                         still has time to confirm); validated {@code >= 1} upstream
     * @param limit            max orders processed this call (batch back-pressure);
     *                         validated {@code 1..1000} upstream
     */
    public ConfirmPaidStaleResult sweep(int olderThanMinutes, int limit) {
        Instant cutoff = Instant.now(clock).minus(Duration.ofMinutes(olderThanMinutes));
        List<Order> candidates = orderRepository.findStalePaidUnconfirmed(cutoff, limit);
        int scanned = candidates.size();
        List<String> confirmedIds = new ArrayList<>();
        int skipped = 0;

        if (scanned > 0) {
            log.info("stale_paid_confirm_batch count={} cutoff={} limit={}", scanned, cutoff, limit);
        }

        for (Order candidate : candidates) {
            String orderId = candidate.getOrderId();
            try {
                StalePaidOrderConfirmHandler.Outcome outcome = handler.confirmIfStillPending(orderId);
                if (outcome == StalePaidOrderConfirmHandler.Outcome.CONFIRMED) {
                    confirmedIds.add(orderId);
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                // Per-order failure isolated (the REQUIRES_NEW TX rolled back). Excluded
                // from the confirmed tally and left for the next tick. Still a 200 partial.
                log.warn("stale_paid_confirm_order_failed orderId={} reason={}",
                        orderId, e.toString());
            }
        }

        return new ConfirmPaidStaleResult(scanned, confirmedIds.size(), skipped, confirmedIds);
    }
}
