package com.wms.notification.application.service.fakes;

import com.wms.notification.application.port.outbound.OutboxPort;
import com.wms.notification.domain.delivery.NotificationDelivery;
import java.util.ArrayList;
import java.util.List;

public class RecordingOutboxPort implements OutboxPort {

    public final List<Entry> rows = new ArrayList<>();

    @Override
    public void writeDeliveryScheduled(NotificationDelivery delivery) {
        rows.add(new Entry("notification.delivery.scheduled", delivery, null));
    }

    @Override
    public void writeDeliveryCompleted(NotificationDelivery delivery, String outcomeCode) {
        rows.add(new Entry("notification.delivered", delivery, outcomeCode));
    }

    public record Entry(String eventType, NotificationDelivery delivery, String outcomeCode) {
    }
}
