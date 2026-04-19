package com.wms.master.adapter.in.web.dto.response;

import com.wms.master.application.result.ZoneResult;
import com.wms.master.domain.model.WarehouseStatus;
import com.wms.master.domain.model.ZoneType;
import java.time.Instant;
import java.util.UUID;

public record ZoneResponse(
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

    public static ZoneResponse from(ZoneResult result) {
        return new ZoneResponse(
                result.id(),
                result.warehouseId(),
                result.zoneCode(),
                result.name(),
                result.zoneType(),
                result.status(),
                result.version(),
                result.createdAt(),
                result.createdBy(),
                result.updatedAt(),
                result.updatedBy());
    }
}
