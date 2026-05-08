package com.wms.admin.api.settings.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.admin.domain.Setting;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record SettingResponse(
        String key,
        String scope,
        UUID warehouseId,
        JsonNode valueJson,
        JsonNode schemaJson,
        String description,
        long version,
        Instant updatedAt,
        String updatedBy) {

    public static SettingResponse from(Setting s, ObjectMapper objectMapper) {
        try {
            JsonNode value = objectMapper.readTree(s.valueJson());
            JsonNode schema = objectMapper.readTree(s.schemaJson());
            return new SettingResponse(
                    s.key(), s.scope().name(), s.warehouseId(),
                    value, schema, s.description(),
                    s.version(), s.updatedAt(), s.updatedBy());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse setting JSON", e);
        }
    }
}
