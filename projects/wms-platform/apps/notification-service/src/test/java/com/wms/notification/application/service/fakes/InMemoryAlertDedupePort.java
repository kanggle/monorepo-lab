package com.wms.notification.application.service.fakes;

import com.wms.notification.application.port.out.AlertDedupePort;
import com.wms.notification.domain.delivery.DedupeOutcome;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class InMemoryAlertDedupePort implements AlertDedupePort {

    public final Map<UUID, DedupeOutcome> rows = new HashMap<>();

    @Override
    public Result recordIfAbsent(UUID eventId, String sourceTopic, DedupeOutcome outcome) {
        if (rows.containsKey(eventId)) {
            return Result.DUPLICATE;
        }
        rows.put(eventId, outcome);
        return Result.INSERTED;
    }

    @Override
    public boolean exists(UUID eventId) {
        return rows.containsKey(eventId);
    }
}
