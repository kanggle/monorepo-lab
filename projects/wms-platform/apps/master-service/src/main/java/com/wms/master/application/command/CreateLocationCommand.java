package com.wms.master.application.command;

import com.wms.master.domain.model.LocationType;
import java.util.UUID;

public record CreateLocationCommand(
        UUID warehouseId,
        UUID zoneId,
        String locationCode,
        String aisle,
        String rack,
        String level,
        String bin,
        LocationType locationType,
        Integer capacityUnits,
        String actorId) {
}
