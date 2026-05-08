package com.wms.admin.application.user;

import java.util.UUID;

public record CreateUserCommand(
        String userCode,
        String email,
        String name,
        String phone,
        UUID defaultWarehouseId,
        String actorId) {
}
