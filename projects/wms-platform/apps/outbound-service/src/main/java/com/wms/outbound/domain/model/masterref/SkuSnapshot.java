package com.wms.outbound.domain.model.masterref;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Local read-model snapshot of a master SKU.
 */
public record SkuSnapshot(
        UUID id,
        String skuCode,
        TrackingType trackingType,
        Status status,
        Instant cachedAt,
        long masterVersion
) {

    public SkuSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(skuCode, "skuCode");
        Objects.requireNonNull(trackingType, "trackingType");
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
