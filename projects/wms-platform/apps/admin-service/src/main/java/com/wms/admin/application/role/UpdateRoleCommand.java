package com.wms.admin.application.role;

import java.util.List;
import java.util.UUID;

public record UpdateRoleCommand(
        UUID id,
        String name,
        String description,
        List<String> permissions,
        String actorId) {
}
