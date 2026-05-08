package com.wms.admin.application.settings;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.UUID;

public record UpsertSettingCommand(
        String key,
        UUID warehouseId,
        JsonNode valueJson,
        String actorId) {
}
