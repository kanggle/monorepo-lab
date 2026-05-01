package com.wms.inbound.application.command;

import java.util.UUID;

public record StartInspectionCommand(
        UUID asnId,
        String actorId
) {}
