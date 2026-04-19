package com.wms.master.adapter.in.web.dto.request;

import com.wms.master.application.command.CreateLocationCommand;
import com.wms.master.domain.model.LocationType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public record CreateLocationRequest(
        @NotBlank(message = "locationCode is required")
        @Size(max = 40, message = "locationCode must be at most 40 characters")
        @Pattern(
                regexp = "^WH\\d{2,3}-[A-Z0-9]+(-[A-Z0-9]+){1,5}$",
                message = "locationCode must match ^WH\\d{2,3}-[A-Z0-9]+(-[A-Z0-9]+){1,5}$")
        String locationCode,

        @Size(max = 10, message = "aisle must be at most 10 characters")
        String aisle,

        @Size(max = 10, message = "rack must be at most 10 characters")
        String rack,

        @Size(max = 10, message = "level must be at most 10 characters")
        String level,

        @Size(max = 10, message = "bin must be at most 10 characters")
        String bin,

        @NotNull(message = "locationType is required")
        LocationType locationType,

        @Min(value = 1, message = "capacityUnits must be >= 1")
        Integer capacityUnits) {

    public CreateLocationCommand toCommand(UUID warehouseId, UUID zoneId, String actorId) {
        return new CreateLocationCommand(
                warehouseId, zoneId, locationCode,
                aisle, rack, level, bin,
                locationType, capacityUnits, actorId);
    }
}
