package com.example.fanplatform.notification.infrastructure.channel;

import com.example.fanplatform.notification.domain.channel.NotificationChannelPort;
import com.example.fanplatform.notification.domain.notification.Notification;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Deterministic logged mock PUSH channel — NO real FCM/APNs call
 * (architecture.md § Channel Mock Boundary). Logs a structured delivery line,
 * returns a synthetic {@code mockpush_<uuid>} ref, and increments
 * {@code notification_channel_deliveries_total{channel=PUSH,outcome}}. A real
 * FCM/APNs adapter is a future increment that re-implements
 * {@link NotificationChannelPort}.
 */
@Slf4j
@Component
public class LoggingPushChannelAdapter implements NotificationChannelPort {

    static final String CHANNEL = "PUSH";
    private static final String METRIC = "notification_channel_deliveries_total";

    private final MeterRegistry meterRegistry;

    public LoggingPushChannelAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public DeliveryResult deliver(Notification notification) {
        String ref = "mockpush_" + UUID.randomUUID();
        log.info("[mock-push] delivered notification id={} account={} type={} ref={}",
                notification.getId(), notification.getAccountId(), notification.getType(), ref);
        meterRegistry.counter(METRIC, "channel", CHANNEL, "outcome", "delivered").increment();
        return new DeliveryResult(true, CHANNEL, ref);
    }
}
