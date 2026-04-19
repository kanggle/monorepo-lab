package com.wms.master.adapter.in.web.dto.request;

import com.wms.master.application.command.UpdateLocationCommand;
import com.wms.master.domain.model.LocationType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * PATCH body for a location update. Caller may NOT supply {@code locationCode},
 * {@code warehouseId}, or {@code zoneId}; any non-null value on those fields is
 * rejected by the domain layer as {@code IMMUTABLE_FIELD} (422). Exposed here
 * on the DTO so attempts are caught, not silently dropped by Jackson.
 */
public record UpdateLocationRequest(
        LocationType locationType,

        @Min(value = 1, message = "capacityUnits must be >= 1")
        Integer capacityUnits,

        @Size(max = 10, message = "aisle must be at most 10 characters")
        String aisle,

        @Size(max = 10, message = "rack must be at most 10 characters")
        String rack,

        @Size(max = 10, message = "level must be at most 10 characters")
        String level,

        @Size(max = 10, message = "bin must be at most 10 characters")
        String bin,

        String locationCode,

        UUID warehouseId,

        UUID zoneId,

        @NotNull(message = "version is required")
        @PositiveOrZero(message = "version must be >= 0")
        Long version) {

    public UpdateLocationCommand toCommand(UUID id, String actorId) {
        return new UpdateLocationCommand(
                id, locationType, capacityUnits,
                aisle, rack, level, bin,
                locationCode, warehouseId, zoneId,
                version, actorId);
    }
}
