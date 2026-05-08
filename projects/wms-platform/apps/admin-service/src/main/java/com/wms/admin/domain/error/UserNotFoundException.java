package com.wms.admin.domain.error;

import java.util.UUID;

public final class UserNotFoundException extends AdminDomainException {
    public UserNotFoundException(UUID id) {
        super("USER_NOT_FOUND", "user not found: " + id);
    }
}
