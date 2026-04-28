package com.wms.inventory.application.result;

import com.wms.inventory.domain.model.Bucket;
import com.wms.inventory.domain.model.ReasonCode;
import com.wms.inventory.domain.model.StockAdjustment;
import java.time.Instant;
import java.util.UUID;

/**
 * Read-side projection of {@link StockAdjustment}.
 */
public record AdjustmentView(
        UUID id,
        UUID inventoryId,
        Bucket bucket,
        int delta,
        ReasonCode reasonCode,
        String reasonNote,
        String actorId,
        Instant createdAt
) {

    public static AdjustmentView from(StockAdjustment adjustment) {
        return new AdjustmentView(
                adjustment.id(),
                adjustment.inventoryId(),
                adjustment.bucket(),
                adjustment.delta(),
                adjustment.reasonCode(),
                adjustment.reasonNote(),
                adjustment.actorId(),
                adjustment.createdAt());
    }
}
