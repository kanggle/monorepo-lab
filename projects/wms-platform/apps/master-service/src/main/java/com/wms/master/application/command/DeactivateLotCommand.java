package com.wms.master.application.command;

import java.util.UUID;

public record DeactivateLotCommand(
        UUID id,
        String reason,
        long version,
        String actorId) {
}
