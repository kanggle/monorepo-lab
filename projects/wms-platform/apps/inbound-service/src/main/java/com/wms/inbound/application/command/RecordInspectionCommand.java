package com.wms.inbound.application.command;

import java.util.List;
import java.util.UUID;

public record RecordInspectionCommand(
        UUID asnId,
        String notes,
        List<Line> lines,
        String actorId
) {
    public record Line(
            UUID asnLineId,
            UUID lotId,
            String lotNo,
            int qtyPassed,
            int qtyDamaged,
            int qtyShort
    ) {}
}
