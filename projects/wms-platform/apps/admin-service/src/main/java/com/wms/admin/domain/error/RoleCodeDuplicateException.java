package com.wms.admin.domain.error;

public final class RoleCodeDuplicateException extends AdminDomainException {
    public RoleCodeDuplicateException(String roleCode) {
        super("ROLE_CODE_DUPLICATE", "role code already taken: " + roleCode);
    }
}
