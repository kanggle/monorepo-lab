package com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto;

import com.example.scmplatform.inventoryvisibility.domain.snapshot.InventorySnapshot;

/**
 * Minimal snapshot row for the internal, network-trusted replenishment-batch
 * endpoint (ADR-MONO-027 §D7.1). {@code availableQty} is the snapshot quantity
 * as a whole-unit integer; {@code nodeId} is the warehouse dimension consumed by
 * demand-planning's {@code SkuWarehouseQty}.
 */
public record InternalSnapshotResponse(String sku, String nodeId, int availableQty) {

    public static InternalSnapshotResponse from(InventorySnapshot s) {
        return new InternalSnapshotResponse(
                s.getSku().value(),
                s.getNodeId().toString(),
                s.getQuantity().value().intValue());
    }
}
