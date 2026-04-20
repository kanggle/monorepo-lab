package com.wms.master.application.command;

import java.util.UUID;

public record ReactivateLotCommand(
        UUID id,
        long version,
        String actorId) {
}
