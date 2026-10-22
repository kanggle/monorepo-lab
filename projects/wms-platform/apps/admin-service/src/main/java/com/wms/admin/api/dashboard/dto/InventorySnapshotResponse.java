package com.wms.admin.api.dashboard.dto;

import com.wms.admin.application.settings.LowStockThresholdReader;
import com.wms.admin.readmodel.inventory.InventorySnapshotEntity;
import java.time.Instant;
import java.util.UUID;

/** Response shape per {@code admin-service-api.md § 1.1}. */
public record InventorySnapshotResponse(
        UUID locationId,
        UUID skuId,
        UUID lotId,
        UUID warehouseId,
        String warehouseCode,
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

    /**
     * {@code lowStockFlag} is derived here, not stored — TASK-BE-644: the
     * caller passes the current setting's threshold, compared with strict
     * {@code <}.
     */
    public static InventorySnapshotResponse from(InventorySnapshotEntity e, int lowStockThreshold) {
        return new InventorySnapshotResponse(
                e.getLocationId(), e.getSkuId(), e.getLotIdOrNull(), e.getWarehouseId(),
                e.getWarehouseCode(), e.getLocationCode(), e.getSkuCode(), e.getLotNo(),
                e.getAvailableQty(), e.getReservedQty(), e.getDamagedQty(), e.getOnHandQty(),
                LowStockThresholdReader.isLowStock(e.getAvailableQty(), lowStockThreshold),
                e.getLastAdjustedAt(), e.getLastEventAt(), e.getVersion());
    }
}
