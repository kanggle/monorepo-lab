package com.wms.master.adapter.in.web.dto.response;

import com.wms.master.application.result.LocationResult;
import com.wms.master.domain.model.LocationType;
import com.wms.master.domain.model.WarehouseStatus;
import java.time.Instant;
import java.util.UUID;

public record LocationResponse(
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

    public static LocationResponse from(LocationResult result) {
        return new LocationResponse(
                result.id(),
                result.warehouseId(),
                result.zoneId(),
                result.locationCode(),
                result.aisle(),
                result.rack(),
                result.level(),
                result.bin(),
                result.locationType(),
                result.capacityUnits(),
                result.status(),
                result.version(),
                result.createdAt(),
                result.createdBy(),
                result.updatedAt(),
                result.updatedBy());
    }
}
