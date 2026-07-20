package com.example.notification.adapter.out.metrics;

import com.example.notification.application.port.out.NotificationMetricsPort;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.NotificationFailureReason;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Micrometer adapter for {@link NotificationMetricsPort} (TASK-BE-533).
 *
 * <p>Mirrors {@code user-service}'s {@code UserMetrics} — a thin {@code MeterRegistry.counter(...)}
 * wrapper, no eager bean-time registration. Registering counters up front would create series that
 * sit permanently at zero, which is the very shape TASK-BE-532 removed the alert rules for; a
 * lazily-created counter only exists once something actually happened.
 */
@Component
@RequiredArgsConstructor
public class MicrometerNotificationMetrics implements NotificationMetricsPort {

    static final String SENT_TOTAL = "notification_sent_total";
    static final String FAILED_TOTAL = "notification_failed_total";
    static final String TAG_CHANNEL = "channel";
    static final String TAG_REASON = "reason";

    private final MeterRegistry registry;

    @Override
    public void recordSent(NotificationChannel channel) {
        registry.counter(SENT_TOTAL, TAG_CHANNEL, tag(channel)).increment();
    }

    @Override
    public void recordFailed(NotificationChannel channel, NotificationFailureReason reason) {
        registry.counter(FAILED_TOTAL,
                        TAG_CHANNEL, tag(channel),
                        TAG_REASON, (reason == null ? NotificationFailureReason.UNKNOWN : reason).tag())
                .increment();
    }

    private String tag(NotificationChannel channel) {
        return channel == null ? "unknown" : channel.name().toLowerCase(Locale.ROOT);
    }
}
