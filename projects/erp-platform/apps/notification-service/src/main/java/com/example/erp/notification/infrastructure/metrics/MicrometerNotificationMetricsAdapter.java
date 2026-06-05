package com.example.erp.notification.infrastructure.metrics;

import com.example.erp.notification.application.port.outbound.NotificationMetricsPort;
import com.example.erp.notification.domain.delivery.DeliveryStatus;
import com.example.erp.notification.domain.notification.NotificationType;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * Micrometer adapter for the notification-service custom metrics
 * (architecture.md § Observability).
 */
@Component
public class MicrometerNotificationMetricsAdapter implements NotificationMetricsPort {

    private final MeterRegistry registry;

    public MicrometerNotificationMetricsAdapter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void dedupeSkipped() {
        registry.counter("notification_event_dedupe_skipped_total").increment();
    }

    @Override
    public void dispatched(NotificationType type) {
        registry.counter("notification_dispatched_total", "type", type.name()).increment();
    }

    @Override
    public void deliveryStatus(DeliveryStatus status) {
        registry.counter("notification_delivery_status_total", "status", status.name()).increment();
    }

    @Override
    public void inboxRead() {
        registry.counter("notification_inbox_read_total").increment();
    }

    @Override
    public void markRead() {
        registry.counter("notification_mark_read_total").increment();
    }
}
