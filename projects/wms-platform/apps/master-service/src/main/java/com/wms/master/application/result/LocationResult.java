package com.wms.master.application.result;

import com.wms.master.domain.model.Location;
import com.wms.master.domain.model.LocationType;
import com.wms.master.domain.model.WarehouseStatus;
import java.time.Instant;
import java.util.UUID;

public record LocationResult(
        UUID id,
        UUID warehouseId,
        UUID zoneId,
        String locationCode,
        String aisle,
        String rack,
        String level,
        String bin,
        LocationType locationType,
        Integer capacityUnits,
        WarehouseStatus status,
        long version,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {

    public static LocationResult from(Location location) {
        return new LocationResult(
                location.getId(),
                location.getWarehouseId(),
                location.getZoneId(),
                location.getLocationCode(),
                location.getAisle(),
                location.getRack(),
                location.getLevel(),
                location.getBin(),
                location.getLocationType(),
                location.getCapacityUnits(),
                location.getStatus(),
                location.getVersion(),
                location.getCreatedAt(),
                location.getCreatedBy(),
                location.getUpdatedAt(),
                location.getUpdatedBy());
    }
}
