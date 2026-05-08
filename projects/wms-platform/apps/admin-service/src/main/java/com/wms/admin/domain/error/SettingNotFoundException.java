package com.wms.admin.domain.error;

public final class SettingNotFoundException extends AdminDomainException {
    public SettingNotFoundException(String key) {
        super("SETTING_NOT_FOUND", "setting not found: " + key);
    }
}
