package com.wms.master.adapter.in.web.dto.request;

import com.wms.master.application.command.ReactivateWarehouseCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.UUID;

public record ReactivateWarehouseRequest(
        @NotNull(message = "version is required")
        @PositiveOrZero(message = "version must be >= 0")
        Long version) {

    public ReactivateWarehouseCommand toCommand(UUID id, String actorId) {
        return new ReactivateWarehouseCommand(id, version, actorId);
    }
}
