package com.example.fanplatform.notification.infrastructure.channel;

import com.example.fanplatform.notification.domain.channel.NotificationChannelPort;
import com.example.fanplatform.notification.domain.notification.Notification;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Deterministic logged mock EMAIL channel — NO real email is sent
 * (architecture.md § Channel Mock Boundary). Logs a structured delivery line,
 * returns a synthetic {@code mockmail_<uuid>} ref, and increments
 * {@code notification_channel_deliveries_total{channel=EMAIL,outcome}}.
 *
 * <p><b>Default EMAIL channel</b> ({@code fanplatform.notification.email.mode=mock},
 * the default). The real {@link HttpEmailChannelAdapter} takes over when the mode is
 * {@code http} — the two are mutually-exclusive {@code @ConditionalOnProperty} beans,
 * so exactly one EMAIL {@link NotificationChannelPort} bean exists (TASK-FAN-BE-016).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "fanplatform.notification.email.mode",
        havingValue = "mock", matchIfMissing = true)
public class LoggingEmailChannelAdapter implements NotificationChannelPort {

    static final String CHANNEL = "EMAIL";
    private static final String METRIC = "notification_channel_deliveries_total";

    private final MeterRegistry meterRegistry;

    public LoggingEmailChannelAdapter(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public DeliveryResult deliver(Notification notification) {
        String ref = "mockmail_" + UUID.randomUUID();
        log.info("[mock-email] delivered notification id={} account={} type={} ref={}",
                notification.getId(), notification.getAccountId(), notification.getType(), ref);
        meterRegistry.counter(METRIC, "channel", CHANNEL, "outcome", "delivered").increment();
        return new DeliveryResult(true, CHANNEL, ref);
    }
}
