package com.wms.inbound.application.command;

import java.util.Set;
import java.util.UUID;

public record SkipPutawayLineCommand(
        UUID putawayInstructionId,
        UUID putawayLineId,
        String reason,
        String actorId,
        Set<String> callerRoles
) {}
