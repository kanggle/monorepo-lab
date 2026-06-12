package com.example.order.application.service;

import com.example.order.application.event.OrderCancelledEvent;
import com.example.order.application.port.OrderEventPublisher;
import com.example.order.application.port.OrderMetricsPort;
import com.example.order.domain.model.Order;
import com.example.order.domain.model.OrderStatus;
import com.example.order.domain.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

/**
 * System-initiated order cancellation for the warehouse-backorder path
 * (ADR-MONO-022 §D4 v2(a), TASK-MONO-197).
 *
 * <p>Unlike {@link OrderCancellationService#cancelOrder} (the user REST path, which
 * enforces {@code requestingUserId == order.userId}), this path is triggered by a wms
 * {@code outbound.order.cancelled} event — there is no requesting user, so ownership is
 * not checked. It reuses the <b>existing</b> {@code order.cancelled} fan-out: emitting the
 * event triggers payment-service refund + promotion-service coupon restore. No new refund
 * machinery is built here.
 *
 * <p>Status-safe + idempotent (the consumer also dedupes on wms {@code eventId}):
 * <ul>
 *   <li>already CANCELLED → no-op (re-delivery, or user-cancelled-first),</li>
 *   <li>SHIPPED / DELIVERED / STUCK_RECOVERY_FAILED → ALERT log + skip (a backorder for a
 *       shipped order is a contract anomaly — never auto-mutate),</li>
 *   <li>PENDING / CONFIRMED → cancel + publish {@code order.cancelled}.</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderBackorderCancellationService {

    private final OrderRepository orderRepository;
    private final OrderEventPublisher orderEventPublisher;
    private final OrderMetricsPort orderMetrics;
    private final Clock clock;

    @Transactional
    public void cancelForBackorder(String orderId, String reason) {
        Order order = orderRepository.findByIdAcrossTenants(orderId).orElse(null);
        if (order == null) {
            log.warn("Backorder cancel for unknown order, skipping. orderId={}, reason={}",
                    orderId, reason);
            return;
        }

        OrderStatus current = order.getStatus();
        if (current == OrderStatus.CANCELLED) {
            log.info("Backorder cancel is a no-op — order already CANCELLED. orderId={}", orderId);
            return;
        }
        if (!current.isCancellable()) {
            // SHIPPED / DELIVERED / STUCK_RECOVERY_FAILED — must not auto-cancel a shipped order.
            log.error("ALERT backorder cancel for non-cancellable order — manual intervention needed. "
                    + "orderId={}, status={}, reason={}", orderId, current, reason);
            return;
        }

        String previousStatus = current.name();
        order.cancel(clock);
        orderRepository.save(order);
        orderMetrics.recordOrderCancelled("backorder");
        orderMetrics.recordStatusTransition(previousStatus, order.getStatus().name());

        log.info("Order auto-cancelled by warehouse backorder. orderId={}, previousStatus={}, reason={}",
                orderId, previousStatus, reason);

        // Reuse the existing cancel fan-out: payment-service refunds + promotion-service restores.
        orderEventPublisher.publishOrderCancelled(
                OrderCancelledEvent.of(order.getOrderId(), order.getUserId(),
                        order.getUpdatedAt(), clock));
    }
}
