package com.wms.inventory.domain.event;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Published when a mutation reduces {@code availableQty} below the configured
 * threshold. Debounced for 1h per inventory row to prevent alert storms.
 *
 * <p>Authoritative payload shape:
 * {@code specs/contracts/events/inventory-events.md} §7.
 */
public record InventoryLowStockDetectedEvent(
        UUID inventoryId,
        UUID locationId,
        String locationCode,
        UUID skuId,
        String skuCode,
        UUID lotId,
        int availableQty,
        int threshold,
        String triggeringEventType,
        UUID triggeringEventId,
        Instant occurredAt,
        String actorId
) implements InventoryDomainEvent {

    public InventoryLowStockDetectedEvent {
        Objects.requireNonNull(inventoryId, "inventoryId");
        Objects.requireNonNull(locationId, "locationId");
        Objects.requireNonNull(skuId, "skuId");
        Objects.requireNonNull(triggeringEventType, "triggeringEventType");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(actorId, "actorId");
    }

    @Override public String eventType() { return "inventory.low-stock-detected"; }
    @Override public String aggregateType() { return "alert"; }
    @Override public UUID aggregateId() { return inventoryId; }
    @Override public String partitionKey() { return locationId.toString(); }
}
