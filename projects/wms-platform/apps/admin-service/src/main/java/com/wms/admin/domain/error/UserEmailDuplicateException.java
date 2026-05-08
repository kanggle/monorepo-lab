package com.wms.admin.domain.error;

public final class UserEmailDuplicateException extends AdminDomainException {
    public UserEmailDuplicateException(String email) {
        super("USER_EMAIL_DUPLICATE", "email already taken: " + email);
    }
}
