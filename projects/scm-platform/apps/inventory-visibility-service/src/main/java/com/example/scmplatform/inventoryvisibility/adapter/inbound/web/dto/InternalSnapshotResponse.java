package com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto;

import com.example.scmplatform.inventoryvisibility.domain.snapshot.InventorySnapshot;

/**
 * Minimal snapshot row for the internal, network-trusted replenishment-batch
 * endpoint (ADR-MONO-027 §D7.1). {@code availableQty} is the snapshot quantity
 * as a whole-unit integer; {@code nodeId} is the warehouse dimension consumed by
 * demand-planning's {@code SkuWarehouseQty}.
 *
 * <p>{@code warehouseCode} (ADR-MONO-050 D9 / TASK-SCM-BE-037) is the owning node's
 * business warehouse code — nullable, since wms resolves it best-effort. Demand-planning's
 * batch sweep uses it to address the replenishment PO by code; when null the PO is still
 * raised, only the wms inbound-expected addressing is omitted (fail-closed, no uuid leak).
 */
public record InternalSnapshotResponse(String sku, String nodeId, int availableQty,
                                       String warehouseCode) {

    public static InternalSnapshotResponse from(InventorySnapshot s, String warehouseCode) {
        return new InternalSnapshotResponse(
                s.getSku().value(),
                s.getNodeId().toString(),
                s.getQuantity().value().intValue(),
                warehouseCode);
    }
}
