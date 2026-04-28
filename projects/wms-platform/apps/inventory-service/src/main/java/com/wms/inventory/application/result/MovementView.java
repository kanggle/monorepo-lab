package com.wms.inventory.application.result;

import com.wms.inventory.domain.model.Bucket;
import com.wms.inventory.domain.model.MovementType;
import com.wms.inventory.domain.model.ReasonCode;
import java.time.Instant;
import java.util.UUID;

public record MovementView(
        UUID id,
        UUID inventoryId,
        MovementType movementType,
        Bucket bucket,
        int delta,
        int qtyBefore,
        int qtyAfter,
        ReasonCode reasonCode,
        String reasonNote,
        UUID reservationId,
        UUID transferId,
        UUID adjustmentId,
        UUID sourceEventId,
        String actorId,
        Instant occurredAt
) {
}
