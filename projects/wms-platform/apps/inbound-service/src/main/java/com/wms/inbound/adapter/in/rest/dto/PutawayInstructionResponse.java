package com.wms.inbound.adapter.in.rest.dto;

import com.wms.inbound.application.result.PutawayInstructionResult;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PutawayInstructionResponse(
        UUID putawayInstructionId,
        UUID asnId,
        String asnStatus,
        UUID warehouseId,
        String plannedBy,
        String status,
        long version,
        Instant createdAt,
        Instant updatedAt,
        List<Line> lines
) {
    public record Line(
            UUID putawayLineId,
            UUID asnLineId,
            UUID skuId,
            UUID lotId,
            String lotNo,
            UUID destinationLocationId,
            String destinationLocationCode,
            int qtyToPutaway,
            String status
    ) {}

    public static PutawayInstructionResponse from(PutawayInstructionResult r) {
        List<Line> lines = r.lines().stream()
                .map(l -> new Line(l.putawayLineId(), l.asnLineId(), l.skuId(),
                        l.lotId(), l.lotNo(), l.destinationLocationId(),
                        l.destinationLocationCode(), l.qtyToPutaway(), l.status()))
                .toList();
        return new PutawayInstructionResponse(
                r.putawayInstructionId(), r.asnId(), r.asnStatus(), r.warehouseId(),
                r.plannedBy(), r.status(), r.version(),
                r.createdAt(), r.updatedAt(), lines);
    }
}
