package com.wms.master.adapter.in.web.dto.request;

import com.wms.master.application.command.ReactivateLocationCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record ReactivateLocationRequest(
        @NotNull(message = "version is required")
        @PositiveOrZero(message = "version must be >= 0")
        Long version) {

    public ReactivateLocationCommand toCommand(UUID id, String actorId) {
        return new ReactivateLocationCommand(id, version, actorId);
    }
}
