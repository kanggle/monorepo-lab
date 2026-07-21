package com.example.fanplatform.notification.infrastructure.channel;

import com.example.fanplatform.notification.domain.channel.NotificationChannelPort;
import com.example.fanplatform.notification.domain.notification.Notification;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Deterministic logged mock PUSH channel — NO real FCM/APNs call
 * (architecture.md § Channel Mock Boundary). Logs a structured delivery line,
 * returns a synthetic {@code mockpush_<uuid>} ref, and increments
 * {@code notification_channel_deliveries_total{channel=PUSH,outcome}}.
 *
 * <p><b>Default PUSH channel</b> ({@code fanplatform.notification.push.mode=mock},
 * the default). The real FCM HTTP v1 {@link HttpFcmPushChannelAdapter} takes over when
 * the mode is {@code fcm} — the two are mutually-exclusive {@code @ConditionalOnProperty}
 * beans, so exactly one PUSH {@link NotificationChannelPort} bean exists
 * (TASK-FAN-BE-017). APNs remains a further increment.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "fanplatform.notification.push.mode",
        havingValue = "mock", matchIfMissing = true)
public class LoggingPushChannelAdapter implements NotificationChannelPort {

    static final String CHANNEL = "PUSH";

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
        ChannelDeliveryMetrics.delivered(meterRegistry, CHANNEL);
        return new DeliveryResult(true, CHANNEL, ref);
    }
}
