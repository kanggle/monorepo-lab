package com.wms.master.application.command;

import java.util.UUID;

public record ReactivateSkuCommand(
        UUID id,
        long version,
        String actorId) {
}
