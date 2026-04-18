package com.wms.master.application.command;

import java.util.UUID;

public record ReactivateWarehouseCommand(
        UUID id,
        long version,
        String actorId) {
}
