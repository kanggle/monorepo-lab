package com.wms.inventory.domain.event;

import com.wms.inventory.domain.model.TransferReasonCode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Published on every successful stock transfer between two locations.
 *
 * <p>Authoritative payload shape:
 * {@code specs/contracts/events/inventory-events.md} §3.
 */
public record InventoryTransferredEvent(
        UUID transferId,
        UUID warehouseId,
        UUID skuId,
        UUID lotId,
        int quantity,
        TransferReasonCode reasonCode,
        Endpoint source,
        Endpoint target,
        Instant occurredAt,
        String actorId
) implements InventoryDomainEvent {

    public InventoryTransferredEvent {
        Objects.requireNonNull(transferId, "transferId");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(skuId, "skuId");
        Objects.requireNonNull(reasonCode, "reasonCode");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(target, "target");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(actorId, "actorId");
    }

    @Override public String eventType() { return "inventory.transferred"; }
    @Override public String aggregateType() { return "stock_transfer"; }
    @Override public UUID aggregateId() { return transferId; }
    @Override public String partitionKey() { return source.locationId().toString(); }

    public record Endpoint(
            UUID locationId,
            String locationCode,
            UUID inventoryId,
            int availableQtyAfter,
            boolean wasCreated
    ) {
    }
}
