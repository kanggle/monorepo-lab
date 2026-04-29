package com.wms.outbound.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code outbound.order.cancelled} — published when an order is cancelled
 * before {@code SHIPPED}. Schema: {@code specs/contracts/events/outbound-events.md} §2.
 */
public record OrderCancelledEvent(
        UUID orderId,
        String orderNo,
        String previousStatus,
        String reason,
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
