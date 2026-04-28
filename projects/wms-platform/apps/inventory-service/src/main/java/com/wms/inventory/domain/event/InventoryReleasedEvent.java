package com.wms.inventory.domain.event;

import com.wms.inventory.domain.model.ReleasedReason;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published when a {@code Reservation} is released (cancellation, TTL expiry,
 * or manual ops release).
 *
 * <p>Authoritative payload shape:
 * {@code specs/contracts/events/inventory-events.md} §5.
 */
public record InventoryReleasedEvent(
        UUID reservationId,
        UUID pickingRequestId,
        UUID warehouseId,
        ReleasedReason releasedReason,
        List<Line> lines,
        Instant releasedAt,
        Instant occurredAt,
        String actorId
) implements InventoryDomainEvent {

    public InventoryReleasedEvent {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("InventoryReleasedEvent must have at least one line");
        }
        lines = List.copyOf(lines);
    }

    @Override public String eventType() { return "inventory.released"; }
    @Override public String aggregateType() { return "reservation"; }
    @Override public UUID aggregateId() { return reservationId; }
    @Override public String partitionKey() { return lines.get(0).locationId().toString(); }

    public record Line(
            UUID reservationLineId,
            UUID inventoryId,
            UUID locationId,
            UUID skuId,
            UUID lotId,
            int quantity,
            int availableQtyAfter,
            int reservedQtyAfter
    ) {
    }
}
