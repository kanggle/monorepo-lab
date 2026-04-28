package com.wms.inventory.adapter.in.web.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wms.inventory.application.result.InventoryView;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record InventoryResponse(
        UUID id,
        UUID warehouseId,
        UUID locationId,
        String locationCode,
        UUID skuId,
        String skuCode,
        UUID lotId,
        String lotNo,
        int availableQty,
        int reservedQty,
        int damagedQty,
        int onHandQty,
        Instant lastMovementAt,
        long version,
        Instant createdAt,
        Instant updatedAt
) {
    public static InventoryResponse from(InventoryView view) {
        return new InventoryResponse(
                view.id(),
                view.warehouseId(),
                view.locationId(),
                view.locationCode(),
                view.skuId(),
                view.skuCode(),
                view.lotId(),
                view.lotNo(),
                view.availableQty(),
                view.reservedQty(),
                view.damagedQty(),
                view.onHandQty(),
                view.lastMovementAt(),
                view.version(),
                view.createdAt(),
                view.updatedAt());
    }
}
