package com.wms.master.application.command;

import java.util.UUID;

public record ReactivateZoneCommand(
        UUID id,
        long version,
        String actorId) {
}
