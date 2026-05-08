package com.wms.admin.application.user;

import java.util.UUID;

public record UpdateUserCommand(
        UUID id,
        String name,
        String email,
        String phone,
        UUID defaultWarehouseId,
        String actorId) {
}
