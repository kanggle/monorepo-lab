package com.wms.inbound.adapter.in.rest.dto;

import com.wms.inbound.application.result.InspectionResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InspectionResponse(
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

    public static InspectionResponse from(InspectionResult r) {
        List<Line> lines = r.lines().stream()
                .map(l -> new Line(l.id(), l.asnLineId(), l.skuId(), l.lotId(), l.lotNo(),
                        l.qtyPassed(), l.qtyDamaged(), l.qtyShort()))
                .toList();
        List<Discrepancy> discs = r.discrepancies().stream()
                .map(d -> new Discrepancy(d.id(), d.asnLineId(), d.discrepancyType(),
                        d.expectedQty(), d.actualTotalQty(), d.variance(),
                        d.acknowledged(), d.acknowledgedBy(), d.acknowledgedAt(), d.notes()))
                .toList();
        return new InspectionResponse(r.id(), r.asnId(), r.inspectorId(), r.completedAt(),
                r.notes(), r.version(), r.createdAt(), lines, discs);
    }
}
