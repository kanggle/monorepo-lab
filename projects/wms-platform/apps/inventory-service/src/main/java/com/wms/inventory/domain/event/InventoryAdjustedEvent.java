package com.wms.inventory.domain.event;

import com.wms.inventory.domain.model.Bucket;
import com.wms.inventory.domain.model.MovementType;
import com.wms.inventory.domain.model.ReasonCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Published on every successful manual adjustment / mark-damaged / write-off.
 *
 * <p>Authoritative payload shape:
 * {@code specs/contracts/events/inventory-events.md} §2.
 */
public record InventoryAdjustedEvent(
        UUID adjustmentId,
        UUID inventoryId,
        UUID locationId,
        UUID skuId,
        UUID lotId,
        Bucket bucket,
        int delta,
        ReasonCode reasonCode,
        String reasonNote,
        MovementType movementType,
        InventorySnapshot inventory,
        Instant occurredAt,
        String actorId
) implements InventoryDomainEvent {

    public InventoryAdjustedEvent {
        Objects.requireNonNull(adjustmentId, "adjustmentId");
        Objects.requireNonNull(inventoryId, "inventoryId");
        Objects.requireNonNull(locationId, "locationId");
        Objects.requireNonNull(skuId, "skuId");
        Objects.requireNonNull(bucket, "bucket");
        Objects.requireNonNull(reasonCode, "reasonCode");
        Objects.requireNonNull(movementType, "movementType");
        Objects.requireNonNull(inventory, "inventory");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(actorId, "actorId");
    }

    @Override public String eventType() { return "inventory.adjusted"; }
    @Override public String aggregateType() { return "stock_adjustment"; }
    @Override public UUID aggregateId() { return adjustmentId; }
    @Override public String partitionKey() { return locationId.toString(); }

    public record InventorySnapshot(
            int availableQty,
            int reservedQty,
            int damagedQty,
            int onHandQty,
            long version
    ) {
    }
}
