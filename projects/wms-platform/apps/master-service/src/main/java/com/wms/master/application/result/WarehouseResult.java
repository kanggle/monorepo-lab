package com.wms.master.application.result;

import com.wms.master.domain.model.Warehouse;
import com.wms.master.domain.model.WarehouseStatus;
import java.time.Instant;
import java.util.UUID;

public record WarehouseResult(
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

    public static WarehouseResult from(Warehouse warehouse) {
        return new WarehouseResult(
                warehouse.getId(),
                warehouse.getWarehouseCode(),
                warehouse.getName(),
                warehouse.getAddress(),
                warehouse.getTimezone(),
                warehouse.getStatus(),
                warehouse.getVersion(),
                warehouse.getCreatedAt(),
                warehouse.getCreatedBy(),
                warehouse.getUpdatedAt(),
                warehouse.getUpdatedBy());
    }
}
