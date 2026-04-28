package com.wms.inventory.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published when a {@code Reservation} is confirmed (W5 — terminal consume).
 *
 * <p>Authoritative payload shape:
 * {@code specs/contracts/events/inventory-events.md} §6.
 *
 * <p>Note that {@code availableQty} is NOT carried — confirmation only
 * decrements the {@code RESERVED} bucket (the qty was already deducted from
 * {@code AVAILABLE} at reserve time).
 */
public record InventoryConfirmedEvent(
        UUID reservationId,
        UUID pickingRequestId,
        UUID warehouseId,
        List<Line> lines,
        Instant confirmedAt,
        Instant occurredAt,
        String actorId
) implements InventoryDomainEvent {

    public InventoryConfirmedEvent {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("InventoryConfirmedEvent must have at least one line");
        }
        lines = List.copyOf(lines);
    }

    @Override public String eventType() { return "inventory.confirmed"; }
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
            int reservedQtyAfter
    ) {
    }
}
