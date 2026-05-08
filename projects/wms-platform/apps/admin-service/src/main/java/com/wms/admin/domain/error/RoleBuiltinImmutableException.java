package com.wms.admin.domain.error;

public final class RoleBuiltinImmutableException extends AdminDomainException {
    public RoleBuiltinImmutableException(String roleCode) {
        super("ROLE_BUILTIN_IMMUTABLE",
                "role " + roleCode + " is built-in — only permissionsJson may be updated");
    }
}
