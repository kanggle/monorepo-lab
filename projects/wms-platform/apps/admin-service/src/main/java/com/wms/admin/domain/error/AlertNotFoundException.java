package com.wms.admin.domain.error;

import java.util.UUID;

public final class AlertNotFoundException extends AdminDomainException {
    public AlertNotFoundException(UUID alertId) {
        super("ALERT_NOT_FOUND", "alert " + alertId + " not found");
    }
}
