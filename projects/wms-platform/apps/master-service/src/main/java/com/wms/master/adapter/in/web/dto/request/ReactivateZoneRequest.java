package com.wms.master.adapter.in.web.dto.request;

import com.wms.master.application.command.ReactivateZoneCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record ReactivateZoneRequest(
        @NotNull(message = "version is required")
        @PositiveOrZero(message = "version must be >= 0")
        Long version) {

    public ReactivateZoneCommand toCommand(UUID id, String actorId) {
        return new ReactivateZoneCommand(id, version, actorId);
    }
}
