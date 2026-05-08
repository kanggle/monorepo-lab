package com.wms.admin.api.role.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wms.admin.application.role.DeactivateRoleResult;
import java.util.List;
import java.util.UUID;

public record DeactivateRoleResponse(RoleResponse role, List<UUID> revokedAssignmentIds) {

    public static DeactivateRoleResponse from(DeactivateRoleResult result, ObjectMapper objectMapper) {
        return new DeactivateRoleResponse(
                RoleResponse.from(result.role(), objectMapper),
                result.revokedAssignmentIds());
    }
}
