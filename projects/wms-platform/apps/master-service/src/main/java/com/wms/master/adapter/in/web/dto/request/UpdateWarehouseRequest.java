package com.wms.master.adapter.in.web.dto.request;

import com.wms.master.application.command.UpdateWarehouseCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record UpdateWarehouseRequest(
        @Size(min = 1, max = 100, message = "name must be 1..100 characters")
        String name,

        @Size(max = 200, message = "address must be <= 200 characters")
        String address,

        String timezone,

        @NotNull(message = "version is required")
        @PositiveOrZero(message = "version must be >= 0")
        Long version) {

    public UpdateWarehouseCommand toCommand(UUID id, String actorId) {
        return new UpdateWarehouseCommand(id, name, address, timezone, version, actorId);
    }
}
