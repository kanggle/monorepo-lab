package com.wms.inventory.adapter.in.web.dto.response;

import com.wms.inventory.application.result.MovementView;
import com.wms.inventory.domain.model.Bucket;
import com.wms.inventory.domain.model.MovementType;
import com.wms.inventory.domain.model.ReasonCode;
import java.time.Instant;
import java.util.UUID;

public record MovementResponse(
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
    public static MovementResponse from(MovementView v) {
        return new MovementResponse(
                v.id(), v.inventoryId(), v.movementType(), v.bucket(),
                v.delta(), v.qtyBefore(), v.qtyAfter(), v.reasonCode(), v.reasonNote(),
                v.reservationId(), v.transferId(), v.adjustmentId(), v.sourceEventId(),
                v.actorId(), v.occurredAt());
    }
}
