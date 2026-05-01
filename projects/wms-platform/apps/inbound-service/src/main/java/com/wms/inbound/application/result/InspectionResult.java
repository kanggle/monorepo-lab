package com.wms.inbound.application.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InspectionResult(
        UUID id,
        UUID asnId,
        String inspectorId,
        Instant completedAt,
        String notes,
        long version,
        Instant createdAt,
        List<Line> lines,
        List<Discrepancy> discrepancies
) {
    public record Line(
            UUID id,
            UUID asnLineId,
            UUID skuId,
            UUID lotId,
            String lotNo,
            int qtyPassed,
            int qtyDamaged,
            int qtyShort
    ) {}

    public record Discrepancy(
            UUID id,
            UUID asnLineId,
            String discrepancyType,
            int expectedQty,
            int actualTotalQty,
            int variance,
            boolean acknowledged,
            String acknowledgedBy,
            Instant acknowledgedAt,
            String notes
    ) {}
}
