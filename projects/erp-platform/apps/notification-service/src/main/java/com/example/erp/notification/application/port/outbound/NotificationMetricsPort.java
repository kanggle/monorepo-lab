package com.example.erp.notification.application.port.outbound;

import com.example.erp.notification.domain.delivery.DeliveryStatus;
import com.example.erp.notification.domain.notification.NotificationType;

/**
 * Outbound port for notification-service custom metrics (architecture.md
 * § Observability). Keeps Micrometer out of the application layer.
 */
public interface NotificationMetricsPort {

    /** A duplicate {@code eventId} was skipped (dedupe, T8). */
    void dedupeSkipped();

    /** A notification was dispatched (type-tagged). */
    void dispatched(NotificationType type);

    /** A delivery reached a terminal status (DELIVERED / FAILED). */
    void deliveryStatus(DeliveryStatus status);

    /** An inbox read (list / detail) occurred. */
    void inboxRead();

    /** A mark-read occurred. */
    void markRead();
}
