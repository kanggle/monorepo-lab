package com.wms.master.adapter.in.web.dto.request;

import com.wms.master.application.command.ReactivateSkuCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record ReactivateSkuRequest(
        @NotNull(message = "version is required")
        @PositiveOrZero(message = "version must be >= 0")
        Long version) {

    public ReactivateSkuCommand toCommand(UUID id, String actorId) {
        return new ReactivateSkuCommand(id, version, actorId);
    }
}
