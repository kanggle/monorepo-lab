package com.wms.master.adapter.in.web.dto.response;

import com.wms.master.application.result.WarehouseResult;
import com.wms.master.domain.model.WarehouseStatus;
import java.time.Instant;
import java.util.UUID;

public record WarehouseResponse(
        UUID id,
        String warehouseCode,
        String name,
        String address,
        String timezone,
        WarehouseStatus status,
        long version,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {

    public static WarehouseResponse from(WarehouseResult result) {
        return new WarehouseResponse(
                result.id(),
                result.warehouseCode(),
                result.name(),
                result.address(),
                result.timezone(),
                result.status(),
                result.version(),
                result.createdAt(),
                result.createdBy(),
                result.updatedAt(),
                result.updatedBy());
    }
}
