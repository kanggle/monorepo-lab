package com.example.scmplatform.demandplanning.application.port.outbound;

import java.util.List;
import java.util.UUID;

/**
 * Outbound port for reading the IVS inventory snapshot read-model (batch sweep).
 * Called only from the nightly ReorderSweepScheduler — never in the live alert path (S5).
 * If IVS is unavailable, the port returns an empty list so the sweep skips gracefully.
 */
public interface InventoryVisibilityPort {

    /**
     * Returns SKU entries with their available quantities across warehouses.
     * Returns empty list if IVS is unavailable (degrades gracefully — batch-job failure mode).
     */
    List<SkuWarehouseQty> findAllBelowReorderPoint(String tenantId);

    record SkuWarehouseQty(String skuCode, UUID warehouseId, int availableQty) {}
}
