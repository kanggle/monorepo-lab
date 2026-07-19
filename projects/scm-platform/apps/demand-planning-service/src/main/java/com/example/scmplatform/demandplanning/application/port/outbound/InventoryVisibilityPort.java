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

    /**
     * A batch sweep candidate.
     *
     * @param warehouseId   the IVS node id — the dedup-key dimension only, never emitted downstream
     * @param warehouseCode the node's business warehouse CODE (ADR-MONO-050 D9 /
     *                      TASK-SCM-BE-037). <b>Nullable</b>: wms resolves it best-effort, so
     *                      IVS may not have learned one yet. A null code never suppresses the
     *                      suggestion — it only omits the wms inbound-expected addressing on
     *                      the materialized PO (fail-closed, no uuid leak).
     */
    record SkuWarehouseQty(String skuCode, UUID warehouseId, int availableQty,
                           String warehouseCode) {}
}
