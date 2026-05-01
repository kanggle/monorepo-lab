package com.wms.inbound.application.command;

import java.util.Set;
import java.util.UUID;

public record CloseAsnCommand(
        UUID asnId,
        long version,
        String actorId,
        Set<String> callerRoles
) {}
