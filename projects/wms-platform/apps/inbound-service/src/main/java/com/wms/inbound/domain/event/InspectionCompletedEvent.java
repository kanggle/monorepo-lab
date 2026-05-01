package com.wms.inbound.domain.event;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InspectionCompletedEvent(
        UUID inspectionId,
        UUID asnId,
        String asnNo,
        UUID warehouseId,
        String inspectorId,
        Instant completedAt,
        List<Line> lines,
        int discrepancyCount,
        List<DiscrepancySummary> discrepancySummary,
        Instant occurredAt,
        String actorId
) implements InboundDomainEvent {

    public record Line(
            UUID inspectionLineId,
            UUID asnLineId,
            UUID skuId,
            UUID lotId,
            String lotNo,
            int expectedQty,
            int qtyPassed,
            int qtyDamaged,
            int qtyShort
    ) {}

    public record DiscrepancySummary(
            UUID discrepancyId,
            UUID asnLineId,
            String discrepancyType,
            int variance,
            boolean acknowledged
    ) {}

    @Override
    public UUID aggregateId() { return inspectionId; }

    @Override
    public String aggregateType() { return "inspection"; }

    @Override
    public String eventType() { return "inbound.inspection.completed"; }

    @Override
    public String partitionKey() { return asnId.toString(); }
}
