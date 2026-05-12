package com.wms.master.application.command;

import java.util.UUID;

public record ReactivatePartnerCommand(
        UUID id,
        long version,
        String actorId) {
}
