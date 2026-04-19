package com.wms.master.application.result;

import com.wms.master.domain.model.WarehouseStatus;
import com.wms.master.domain.model.Zone;
import com.wms.master.domain.model.ZoneType;
import java.time.Instant;
import java.util.UUID;

public record ZoneResult(
        UUID id,
        UUID warehouseId,
        String zoneCode,
        String name,
        ZoneType zoneType,
        WarehouseStatus status,
        long version,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {

    public static ZoneResult from(Zone zone) {
        return new ZoneResult(
                zone.getId(),
                zone.getWarehouseId(),
                zone.getZoneCode(),
                zone.getName(),
                zone.getZoneType(),
                zone.getStatus(),
                zone.getVersion(),
                zone.getCreatedAt(),
                zone.getCreatedBy(),
                zone.getUpdatedAt(),
                zone.getUpdatedBy());
    }
}
