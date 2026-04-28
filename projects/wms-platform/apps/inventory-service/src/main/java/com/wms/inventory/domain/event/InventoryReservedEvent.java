package com.wms.inventory.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published when a {@code Reservation} is successfully created (W4 reserve).
 *
 * <p>Authoritative payload shape:
 * {@code specs/contracts/events/inventory-events.md} §4.
 *
 * <p>{@link #aggregateId()} is the {@code Reservation.id}; partition key is
 * the first line's {@code locationId} per the topic layout.
 */
public record InventoryReservedEvent(
        UUID reservationId,
        UUID pickingRequestId,
        UUID warehouseId,
        Instant expiresAt,
        List<Line> lines,
        Instant occurredAt,
        String actorId
) implements InventoryDomainEvent {

    public InventoryReservedEvent {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("InventoryReservedEvent must have at least one line");
        }
        lines = List.copyOf(lines);
    }

    @Override public String eventType() { return "inventory.reserved"; }
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
