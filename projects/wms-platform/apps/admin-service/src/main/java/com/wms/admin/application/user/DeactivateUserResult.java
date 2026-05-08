package com.wms.admin.application.user;

import com.wms.admin.domain.User;
import java.util.List;
import java.util.UUID;

public record DeactivateUserResult(User user, List<UUID> revokedAssignmentIds) {
}
