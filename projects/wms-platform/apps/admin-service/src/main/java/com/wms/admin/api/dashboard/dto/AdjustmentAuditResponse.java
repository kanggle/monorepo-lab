package com.wms.admin.api.dashboard.dto;

import com.wms.admin.readmodel.inventory.AdjustmentAuditEntity;
import java.time.Instant;
import java.util.UUID;

public record AdjustmentAuditResponse(
        UUID id,
        UUID locationId,
        UUID skuId,
        UUID lotId,
        UUID warehouseId,
        String bucket,
        int delta,
        String reasonCode,
        String reasonNote,
        String actorId,
        Instant occurredAt,
        Instant projectedAt) {

    public static AdjustmentAuditResponse from(AdjustmentAuditEntity e) {
        return new AdjustmentAuditResponse(
                e.getId(), e.getLocationId(), e.getSkuId(), e.getLotId(),
                e.getWarehouseId(), e.getBucket(), e.getDelta(),
                e.getReasonCode(), e.getReasonNote(), e.getActorId(),
                e.getOccurredAt(), e.getProjectedAt());
    }
}
