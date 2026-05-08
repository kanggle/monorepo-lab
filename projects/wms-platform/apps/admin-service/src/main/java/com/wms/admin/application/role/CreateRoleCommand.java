package com.wms.admin.application.role;

import java.util.List;

public record CreateRoleCommand(
        String roleCode,
        String name,
        String description,
        List<String> permissions,
        String actorId) {
}
