package com.wms.master.adapter.in.web.dto.request;

import com.wms.master.application.command.ReactivateLotCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record ReactivateLotRequest(
        @NotNull(message = "version is required")
        @PositiveOrZero(message = "version must be >= 0")
        Long version) {

    public ReactivateLotCommand toCommand(UUID id, String actorId) {
        return new ReactivateLotCommand(id, version, actorId);
    }
}
