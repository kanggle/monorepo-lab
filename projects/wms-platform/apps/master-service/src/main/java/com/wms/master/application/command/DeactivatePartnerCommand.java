package com.wms.master.application.command;

import java.util.UUID;

public record DeactivatePartnerCommand(
        UUID id,
        String reason,
        long version,
        String actorId) {
}
