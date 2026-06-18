package com.wms.outbound.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code outbound.order.cancelled} — published when an order is cancelled
 * before {@code SHIPPED}. Schema: {@code specs/contracts/events/outbound-events.md} §2.
 *
 * <p>{@code tenantId} is additive (ADR-MONO-022 facet d, TASK-MONO-296): the
 * opaque ecommerce-tenant correlation echoed on the outer envelope (also fired on
 * the auto-backorder path) so the ecommerce order-service consumer re-binds the
 * originating tenant for the auto-cancel + refund. {@code null} for B2B /
 * standalone orders (then omitted from the envelope). wms never interprets it.
 */
public record OrderCancelledEvent(
        UUID orderId,
        String orderNo,
        String previousStatus,
        String reason,
        String tenantId,
        Instant cancelledAt,
        Instant occurredAt,
        String actorId
) implements OutboundDomainEvent {

    @Override
    public UUID aggregateId() {
        return orderId;
    }

    @Override
    public String aggregateType() {
        return "order";
    }

    @Override
    public String eventType() {
        return "outbound.order.cancelled";
    }

    @Override
    public String partitionKey() {
        return orderId.toString();
    }
}
