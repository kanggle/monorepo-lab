package com.wms.inbound.application.result;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record PutawayInstructionResult(
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
}
