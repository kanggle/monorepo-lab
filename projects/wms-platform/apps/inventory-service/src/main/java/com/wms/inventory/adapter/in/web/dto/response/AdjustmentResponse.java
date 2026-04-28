package com.wms.inventory.adapter.in.web.dto.response;

import com.wms.inventory.application.result.AdjustmentResult;
import com.wms.inventory.application.result.AdjustmentView;
import com.wms.inventory.domain.model.Bucket;
import com.wms.inventory.domain.model.ReasonCode;
import java.time.Instant;
import java.util.UUID;

public record AdjustmentResponse(
        UUID adjustmentId,
        UUID inventoryId,
        Bucket bucket,
        int delta,
        ReasonCode reasonCode,
        String reasonNote,
        Inventory inventory,
        String actorId,
        Instant createdAt
) {

    public static AdjustmentResponse from(AdjustmentResult result) {
        AdjustmentView a = result.adjustment();
        return new AdjustmentResponse(
                a.id(), a.inventoryId(), a.bucket(), a.delta(),
                a.reasonCode(), a.reasonNote(),
                Inventory.from(result.inventory()),
                a.actorId(), a.createdAt());
    }

    public static AdjustmentResponse fromView(AdjustmentView a) {
        return new AdjustmentResponse(
                a.id(), a.inventoryId(), a.bucket(), a.delta(),
                a.reasonCode(), a.reasonNote(), null,
                a.actorId(), a.createdAt());
    }

    public record Inventory(
            UUID id,
            int availableQty,
            int reservedQty,
            int damagedQty,
            int onHandQty,
            long version
    ) {

        static Inventory from(AdjustmentResult.InventorySnapshot s) {
            return new Inventory(s.id(), s.availableQty(), s.reservedQty(),
                    s.damagedQty(), s.onHandQty(), s.version());
        }
    }
}
