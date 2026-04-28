package com.wms.inventory.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Published when {@code PutawayCompletedConsumer} processes
 * {@code inbound.putaway.completed} and writes one or more receive movements.
 *
 * <p>One event per consumed putaway — covers all lines. {@link #aggregateId()}
 * is the first affected Inventory row's id (per
 * {@code inventory-events.md} §1).
 */
public record InventoryReceivedEvent(
        UUID warehouseId,
        UUID sourceEventId,
        UUID asnId,
        List<Line> lines,
        Instant occurredAt,
        String actorId
) implements InventoryDomainEvent {

    public InventoryReceivedEvent {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("InventoryReceivedEvent must have at least one line");
        }
        lines = List.copyOf(lines);
    }

    @Override
    public String eventType() {
        return "inventory.received";
    }

    @Override
    public String aggregateType() {
        return "inventory";
    }

    @Override
    public UUID aggregateId() {
        return lines.get(0).inventoryId();
    }

    @Override
    public String partitionKey() {
        return lines.get(0).locationId().toString();
    }

    public record Line(
            UUID inventoryId,
            UUID locationId,
            String locationCode,
            UUID skuId,
            UUID lotId,
            int qtyReceived,
            int availableQtyAfter
    ) {
    }
}
