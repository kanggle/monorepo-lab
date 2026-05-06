package com.example.scmplatform.inventoryvisibility.adapter.inbound.web.dto;

import com.example.scmplatform.inventoryvisibility.domain.snapshot.InventorySnapshot;
import com.example.scmplatform.inventoryvisibility.domain.staleness.StalenessStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record SnapshotResponse(
        String id,
        String nodeId,
        String sku,
        BigDecimal quantity,
        Instant lastEventAt,
        int version,
        String staleness
) {
    public static SnapshotResponse from(InventorySnapshot s, StalenessStatus staleness) {
        return new SnapshotResponse(
                s.getId().toString(),
                s.getNodeId().toString(),
                s.getSku().value(),
                s.getQuantity().value(),
                s.getLastEventAt(),
                s.getVersion(),
                staleness != null ? staleness.name() : StalenessStatus.FRESH.name()
        );
    }
}
