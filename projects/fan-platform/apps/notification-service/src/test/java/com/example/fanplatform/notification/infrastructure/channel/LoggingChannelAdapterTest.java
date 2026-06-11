package com.example.fanplatform.notification.infrastructure.channel;

import com.example.fanplatform.notification.domain.channel.NotificationChannelPort;
import com.example.fanplatform.notification.domain.notification.Notification;
import com.example.fanplatform.notification.domain.notification.NotificationType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class LoggingChannelAdapterTest {

    private static Notification sample() {
        return Notification.create("n1", "fan-platform", "acc-1", NotificationType.WELCOME,
                "t", "b", "evt-1", "fan.membership.activated", "mem-1",
                Instant.parse("2026-06-11T08:00:00Z"));
    }

    @Test
    void emailAdapterReturnsDeliveredWithMockRefAndCounts() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LoggingEmailChannelAdapter adapter = new LoggingEmailChannelAdapter(registry);

        NotificationChannelPort.DeliveryResult result = adapter.deliver(sample());

        assertThat(adapter.channel()).isEqualTo("EMAIL");
        assertThat(result.delivered()).isTrue();
        assertThat(result.channel()).isEqualTo("EMAIL");
        assertThat(result.ref()).startsWith("mockmail_");
        assertThat(registry.counter("notification_channel_deliveries_total",
                "channel", "EMAIL", "outcome", "delivered").count()).isEqualTo(1.0);
    }

    @Test
    void pushAdapterReturnsDeliveredWithMockRefAndCounts() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        LoggingPushChannelAdapter adapter = new LoggingPushChannelAdapter(registry);

        NotificationChannelPort.DeliveryResult result = adapter.deliver(sample());

        assertThat(adapter.channel()).isEqualTo("PUSH");
        assertThat(result.delivered()).isTrue();
        assertThat(result.ref()).startsWith("mockpush_");
        assertThat(registry.counter("notification_channel_deliveries_total",
                "channel", "PUSH", "outcome", "delivered").count()).isEqualTo(1.0);
    }
}
