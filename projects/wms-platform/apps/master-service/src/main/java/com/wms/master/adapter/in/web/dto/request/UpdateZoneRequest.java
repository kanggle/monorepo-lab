package com.wms.master.adapter.in.web.dto.request;

import com.wms.master.application.command.UpdateZoneCommand;
import com.wms.master.domain.model.ZoneType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * PATCH body for a zone update. Caller may NOT supply {@code zoneCode} or
 * {@code warehouseId}; any non-null value on those fields is rejected by the
 * domain layer as {@code IMMUTABLE_FIELD} (422). Exposed here on the DTO so
 * attempts are caught, not silently dropped by Jackson.
 */
public record UpdateZoneRequest(
        @Size(min = 1, max = 100, message = "name must be 1..100 characters")
        String name,

        ZoneType zoneType,

        String zoneCode,

        UUID warehouseId,

        @NotNull(message = "version is required")
        @PositiveOrZero(message = "version must be >= 0")
        Long version) {

    public UpdateZoneCommand toCommand(UUID id, String actorId) {
        return new UpdateZoneCommand(id, name, zoneType, zoneCode, warehouseId, version, actorId);
    }
}
