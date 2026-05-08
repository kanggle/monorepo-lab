package com.wms.admin.api.user.dto;

import com.wms.admin.application.user.DeactivateUserResult;
import java.util.List;
import java.util.UUID;

public record DeactivateUserResponse(UserResponse user, List<UUID> revokedAssignmentIds) {

    public static DeactivateUserResponse from(DeactivateUserResult result) {
        return new DeactivateUserResponse(UserResponse.from(result.user()), result.revokedAssignmentIds());
    }
}
