package com.wms.admin.domain.error;

public final class RoleInUseException extends AdminDomainException {
    public RoleInUseException(int activeCount) {
        super("ROLE_IN_USE",
                "role is referenced by " + activeCount + " active assignments — supply force=true (WMS_SUPERADMIN required)");
    }
}
