package com.wms.inventory.domain.model.masterref;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Local read-model snapshot of a master SKU.
 *
 * <p>Populated by {@code MasterSkuConsumer} from {@code wms.master.sku.v1}
 * events. Drives the {@code lot_id} requirement on Inventory rows: SKUs whose
 * {@link TrackingType} is {@code LOT} must have a non-null {@code lot_id};
 * SKUs whose tracking type is {@code NONE} must have a null {@code lot_id}.
 */
public record SkuSnapshot(
        UUID id,
        String skuCode,
        TrackingType trackingType,
        String baseUom,
        Status status,
        Instant cachedAt,
        long masterVersion
) {

    public SkuSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(skuCode, "skuCode");
        Objects.requireNonNull(trackingType, "trackingType");
        Objects.requireNonNull(baseUom, "baseUom");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(cachedAt, "cachedAt");
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public boolean requiresLot() {
        return trackingType == TrackingType.LOT;
    }

    public enum TrackingType {
        NONE,
        LOT
    }

    public enum Status {
        ACTIVE,
        INACTIVE
    }
}
