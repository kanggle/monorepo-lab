package com.wms.inbound.domain.model.masterref;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Local read-model snapshot of a master Location.
 *
 * <p>Populated by {@code MasterLocationConsumer} from
 * {@code wms.master.location.v1} events. Drives putaway destination validation
 * ({@code LOCATION_INACTIVE}, {@code WAREHOUSE_MISMATCH}).
 */
public record LocationSnapshot(
        UUID id,
        String locationCode,
        UUID warehouseId,
        UUID zoneId,
        LocationType locationType,
        Status status,
        Instant cachedAt,
        long masterVersion
) {

    public LocationSnapshot {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(locationCode, "locationCode");
        Objects.requireNonNull(warehouseId, "warehouseId");
        Objects.requireNonNull(zoneId, "zoneId");
        Objects.requireNonNull(locationType, "locationType");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(cachedAt, "cachedAt");
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public enum LocationType {
        STORAGE,
        STAGING_INBOUND,
        STAGING_OUTBOUND,
        QUARANTINE,
        DAMAGED,
        DOCK
    }

    public enum Status {
        ACTIVE,
        INACTIVE
    }
}
