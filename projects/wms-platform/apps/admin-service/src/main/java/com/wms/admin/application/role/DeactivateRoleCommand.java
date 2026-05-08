package com.wms.admin.application.role;

import java.util.UUID;

public record DeactivateRoleCommand(
        UUID id,
        boolean force,
        String actorId,
        boolean callerIsSuperadmin) {
}
