package com.wms.admin.api.settings.dto;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record UpsertSettingRequest(
        UUID warehouseId,
        @NotNull JsonNode valueJson) {
}
