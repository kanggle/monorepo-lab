package com.wms.master.application.command;

import com.wms.master.domain.model.ZoneType;
import java.util.UUID;

public record CreateZoneCommand(
        UUID warehouseId,
        String zoneCode,
        String name,
        ZoneType zoneType,
        String actorId) {
}
