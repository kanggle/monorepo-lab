package com.wms.admin.api.role.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.admin.domain.Role;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record RoleResponse(
        UUID id,
        String roleCode,
        String name,
        String description,
        List<String> permissionsJson,
        String status,
        boolean isBuiltin,
        long version,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {

    public static RoleResponse from(Role r, ObjectMapper objectMapper) {
        List<String> perms;
        try {
            perms = objectMapper.readValue(r.permissionsJson(),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            perms = List.of();
        }
        return new RoleResponse(
                r.id(), r.roleCode(), r.name(), r.description(), perms,
                r.status().name(), r.isBuiltin(), r.version(),
                r.createdAt(), r.createdBy(), r.updatedAt(), r.updatedBy());
    }
}
