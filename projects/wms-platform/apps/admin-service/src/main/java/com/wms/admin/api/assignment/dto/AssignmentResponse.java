package com.wms.admin.api.assignment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wms.admin.domain.UserRoleAssignment;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record AssignmentResponse(
        UUID id,
        UUID userId,
        UUID roleId,
        UUID warehouseId,
        String status,
        Instant grantedAt,
        String grantedBy,
        Instant revokedAt,
        String revokedBy,
        long version,
        Instant createdAt,
        Instant updatedAt) {

    public static AssignmentResponse from(UserRoleAssignment a) {
        return new AssignmentResponse(
                a.id(), a.userId(), a.roleId(), a.warehouseId(),
                a.status().name(),
                a.grantedAt(), a.grantedBy(),
                a.revokedAt(), a.revokedBy(),
                a.version(), a.createdAt(), a.updatedAt());
    }
}
