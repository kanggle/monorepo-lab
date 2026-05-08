package com.wms.admin.domain.error;

public final class UserHasActiveAssignmentsException extends AdminDomainException {
    public UserHasActiveAssignmentsException(int activeCount) {
        super("USER_HAS_ACTIVE_ASSIGNMENTS",
                "user has " + activeCount + " active assignments — supply force=true (WMS_SUPERADMIN required)");
    }
}
