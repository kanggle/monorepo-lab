package com.wms.master.adapter.in.web.dto.request;

import com.wms.master.application.command.CreateZoneCommand;
import com.wms.master.domain.model.ZoneType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateZoneRequest(
        @NotBlank(message = "zoneCode is required")
        @Pattern(regexp = "^Z-[A-Z0-9]+$", message = "zoneCode must match ^Z-[A-Z0-9]+$")
        String zoneCode,

        @NotBlank(message = "name is required")
        @Size(min = 1, max = 100, message = "name must be 1..100 characters")
        String name,

        @NotNull(message = "zoneType is required")
        ZoneType zoneType) {

    public CreateZoneCommand toCommand(UUID warehouseId, String actorId) {
        return new CreateZoneCommand(warehouseId, zoneCode, name, zoneType, actorId);
    }
}
