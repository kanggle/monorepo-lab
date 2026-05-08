package com.wms.admin.domain.error;

public final class SettingImmutableFieldException extends AdminDomainException {
    public SettingImmutableFieldException(String field) {
        super("SETTING_IMMUTABLE_FIELD", "setting field " + field + " is immutable after creation");
    }
}
