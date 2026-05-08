package com.wms.admin.domain.error;

import java.util.UUID;

public final class RoleNotFoundException extends AdminDomainException {
    public RoleNotFoundException(UUID id) {
        super("ROLE_NOT_FOUND", "role not found: " + id);
    }
}
