package com.wms.inbound.application.command;

import java.util.Set;
import java.util.UUID;

public record ConfirmPutawayLineCommand(
        UUID putawayInstructionId,
        UUID putawayLineId,
        UUID actualLocationId,
        int qtyConfirmed,
        String actorId,
        Set<String> callerRoles
) {}
