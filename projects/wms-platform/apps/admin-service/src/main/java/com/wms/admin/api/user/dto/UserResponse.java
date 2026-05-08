package com.wms.admin.api.user.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.wms.admin.domain.User;
import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.ALWAYS)
public record UserResponse(
        UUID id,
        String userCode,
        String email,
        String name,
        String phone,
        UUID defaultWarehouseId,
        String status,
        long version,
        Instant createdAt,
        String createdBy,
        Instant updatedAt,
        String updatedBy) {

    public static UserResponse from(User u) {
        return new UserResponse(
                u.id(), u.userCode(), u.email(), u.name(), u.phone(),
                u.defaultWarehouseId(), u.status().name(), u.version(),
                u.createdAt(), u.createdBy(), u.updatedAt(), u.updatedBy());
    }
}
