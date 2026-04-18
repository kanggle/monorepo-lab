package com.wms.master.application.command;

import java.util.UUID;

public record DeactivateWarehouseCommand(
        UUID id,
        String reason,
        long version,
        String actorId) {
}
