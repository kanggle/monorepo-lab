package com.wms.admin.api.dashboard.dto;

import com.wms.admin.readmodel.inventory.InventorySnapshotEntity;
import java.time.Instant;
import java.util.UUID;

/** Response shape per {@code admin-service-api.md § 1.1}. */
public record InventorySnapshotResponse(
        UUID locationId,
        UUID skuId,
        UUID lotId,
        UUID warehouseId,
        String locationCode,
        String skuCode,
        String lotNo,
        int availableQty,
        int reservedQty,
        int damagedQty,
        int onHandQty,
        boolean lowStockFlag,
        Instant lastAdjustedAt,
        Instant lastEventAt,
        long version) {

    public static InventorySnapshotResponse from(InventorySnapshotEntity e) {
        return new InventorySnapshotResponse(
                e.getLocationId(), e.getSkuId(), e.getLotIdOrNull(), e.getWarehouseId(),
                e.getLocationCode(), e.getSkuCode(), e.getLotNo(),
                e.getAvailableQty(), e.getReservedQty(), e.getDamagedQty(), e.getOnHandQty(),
                e.isLowStockFlag(), e.getLastAdjustedAt(), e.getLastEventAt(), e.getVersion());
    }
}
