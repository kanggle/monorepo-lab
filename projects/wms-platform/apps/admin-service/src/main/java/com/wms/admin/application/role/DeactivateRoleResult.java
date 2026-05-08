package com.wms.admin.application.role;

import com.wms.admin.domain.Role;
import java.util.List;
import java.util.UUID;

public record DeactivateRoleResult(Role role, List<UUID> revokedAssignmentIds) {
}
