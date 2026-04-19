package com.wms.master.application.command;

import java.util.UUID;

public record ReactivateLocationCommand(
        UUID id,
        long version,
        String actorId) {
}
