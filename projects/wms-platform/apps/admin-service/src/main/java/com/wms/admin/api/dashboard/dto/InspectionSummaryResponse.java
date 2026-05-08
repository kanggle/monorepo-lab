package com.wms.admin.api.dashboard.dto;

import com.wms.admin.readmodel.inbound.InspectionSummaryEntity;
import java.time.Instant;
import java.util.UUID;

public record InspectionSummaryResponse(
        UUID asnId,
        UUID warehouseId,
        Instant inspectionCompletedAt,
        String inspectorId,
        int totalLines,
        int discrepancyCount,
        int totalQtyExpected,
        int totalQtyPassed,
        int totalQtyDamaged,
        int totalQtyShort,
        Instant lastEventAt,
        long version) {

    public static InspectionSummaryResponse from(InspectionSummaryEntity e) {
        return new InspectionSummaryResponse(
                e.getAsnId(), e.getWarehouseId(), e.getInspectionCompletedAt(),
                e.getInspectorId(), e.getTotalLines(), e.getDiscrepancyCount(),
                e.getTotalQtyExpected(), e.getTotalQtyPassed(),
                e.getTotalQtyDamaged(), e.getTotalQtyShort(),
                e.getLastEventAt(), e.getVersion());
    }
}
