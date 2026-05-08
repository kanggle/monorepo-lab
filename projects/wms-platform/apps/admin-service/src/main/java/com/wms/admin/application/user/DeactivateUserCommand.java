package com.wms.admin.application.user;

import java.util.UUID;

public record DeactivateUserCommand(
        UUID id,
        boolean force,
        String actorId,
        boolean callerIsSuperadmin) {
}
