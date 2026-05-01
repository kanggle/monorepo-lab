package com.wms.inbound.application.command;

import java.util.Set;
import java.util.UUID;

public record CancelAsnCommand(
        UUID asnId,
        String reason,
        long version,
        String actorId,
        Set<String> callerRoles
) {}
