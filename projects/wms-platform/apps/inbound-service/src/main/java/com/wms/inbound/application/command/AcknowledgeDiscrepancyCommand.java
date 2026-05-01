package com.wms.inbound.application.command;

import java.util.Set;
import java.util.UUID;

public record AcknowledgeDiscrepancyCommand(
        UUID inspectionId,
        UUID discrepancyId,
        String notes,
        String actorId,
        Set<String> callerRoles
) {}
