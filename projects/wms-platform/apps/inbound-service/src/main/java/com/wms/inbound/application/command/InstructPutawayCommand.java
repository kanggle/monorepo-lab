package com.wms.inbound.application.command;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public record InstructPutawayCommand(
        UUID asnId,
        List<Line> lines,
        long version,
        String actorId,
        Set<String> callerRoles
) {
    public record Line(
            UUID asnLineId,
            UUID destinationLocationId,
            int qtyToPutaway
    ) {}
}
