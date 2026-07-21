package com.example.fanplatform.notification.infrastructure.channel;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * Single-sources the notification delivery metric name and tag shape shared by
 * the 4 channel adapters (TASK-FAN-BE-025 N3). The {@code METRIC} constant and the
 * {@code counter(METRIC, "channel", CHANNEL, "outcome", …).increment()} recording
 * were duplicated verbatim ×4 (delivered) / ×2 (failed).
 *
 * <p>Kept as a static helper taking the adapter's own {@link MeterRegistry} so each
 * adapter's constructor and fields — hence its existing unit tests — are unchanged.
 * The per-adapter log lines stay in the adapters (they differ by channel and by the
 * fields logged). The emitted metric name and its {@code channel}/{@code outcome}
 * tags are byte-identical to the pre-refactor calls.
 */
final class ChannelDeliveryMetrics {

    static final String METRIC = "notification_channel_deliveries_total";

    private ChannelDeliveryMetrics() {
    }

    /** Record a successful delivery on {@code METRIC{channel=<channel>,outcome=delivered}}. */
    static void delivered(MeterRegistry meterRegistry, String channel) {
        record(meterRegistry, channel, "delivered");
    }

    /** Record a failed delivery on {@code METRIC{channel=<channel>,outcome=failed}}. */
    static void failed(MeterRegistry meterRegistry, String channel) {
        record(meterRegistry, channel, "failed");
    }

    private static void record(MeterRegistry meterRegistry, String channel, String outcome) {
        meterRegistry.counter(METRIC, "channel", channel, "outcome", outcome).increment();
    }
}
