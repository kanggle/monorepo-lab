package com.wms.notification.application.port.out;

import com.wms.notification.domain.delivery.NotificationDelivery;

/**
 * Outbox port — appends one row per significant lifecycle transition so
 * downstream services (admin-service v2 dashboards) can subscribe to
 * delivery audit events. T3 of the {@code transactional} trait.
 */
public interface OutboxPort {

    /** Append the {@code notification.delivery.scheduled} event when a new delivery is queued. */
    void writeDeliveryScheduled(NotificationDelivery delivery);

    /** Append the {@code notification.delivered} event on terminal SUCCEEDED / FAILED. */
    void writeDeliveryCompleted(NotificationDelivery delivery, String outcomeCode);
}
