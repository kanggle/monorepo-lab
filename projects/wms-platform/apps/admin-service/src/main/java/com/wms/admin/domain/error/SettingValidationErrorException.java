package com.wms.admin.domain.error;

public final class SettingValidationErrorException extends AdminDomainException {
    public SettingValidationErrorException(String detail) {
        super("SETTING_VALIDATION_ERROR", detail);
    }
}
