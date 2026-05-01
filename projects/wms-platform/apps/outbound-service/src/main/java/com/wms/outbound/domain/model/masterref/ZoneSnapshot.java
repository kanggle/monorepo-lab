package com.wms.outbound.domain.model.masterref;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Local read-model snapshot of a master Zone.
 */
public record ZoneSnapshot(
        UUID id,
        UUID warehouseId,
        String zoneCode,
        String zoneType,
        Status status,
        Instant cachedAt,
        long masterVersion
) {

    public ZoneSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(zoneCode, "zoneCode");
        Objects.requireNonNull(zoneType, "zoneType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(cachedAt, "cachedAt");
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public enum Status {
        ACTIVE,
        INACTIVE
    }
}
