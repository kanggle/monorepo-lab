package com.wms.admin.application.assignment;

import java.util.UUID;

public record GrantAssignmentCommand(
        UUID userId,
        UUID roleId,
        UUID warehouseId,
        String actorId) {
}
