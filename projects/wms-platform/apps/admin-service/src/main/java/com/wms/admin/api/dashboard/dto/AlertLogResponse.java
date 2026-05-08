package com.wms.admin.api.dashboard.dto;

import com.wms.admin.readmodel.alert.AlertLogEntity;
import java.time.Instant;
import java.util.UUID;

public record AlertLogResponse(
        UUID id,
        String alertType,
        UUID warehouseId,
        UUID locationId,
        UUID skuId,
        UUID lotId,
        Integer thresholdQty,
        Integer actualQty,
        Instant detectedAt,
        Instant acknowledgedAt,
        String acknowledgedBy,
        Instant projectedAt) {

    public static AlertLogResponse from(AlertLogEntity e) {
        return new AlertLogResponse(
                e.getId(), e.getAlertType(), e.getWarehouseId(),
                e.getLocationId(), e.getSkuId(), e.getLotId(),
                e.getThresholdQty(), e.getActualQty(),
                e.getDetectedAt(), e.getAcknowledgedAt(), e.getAcknowledgedBy(),
                e.getProjectedAt());
    }
}
