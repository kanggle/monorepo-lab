package com.example.fanplatform.notification.domain.channel;

import com.example.fanplatform.notification.domain.notification.Notification;

/**
 * The ONLY boundary to delivery side effects (architecture.md § Channel Mock
 * Boundary). v1 ships two deterministic logged mock adapters
 * ({@code LoggingEmailChannelAdapter} + {@code LoggingPushChannelAdapter}) — there
 * is NO real external channel integration. A real adapter (FCM/APNs/SES) is a
 * future increment that re-implements this port, wired via
 * {@code @ConditionalOnMissingBean} / profile, with the mock retained for dev +
 * integration tests. The domain and use-case layers are unchanged by that swap.
 */
public interface NotificationChannelPort {

    /** The channel this adapter delivers on (e.g. {@code EMAIL}, {@code PUSH}). */
    String channel();

    /**
     * Attempts to deliver the (already-persisted) notification on this channel.
     * For the v1 mocks this logs a structured delivery line and returns a
     * synthetic reference; it never performs real I/O.
     */
    DeliveryResult deliver(Notification notification);

    /** Outcome of a delivery attempt. */
    record DeliveryResult(boolean delivered, String channel, String ref) {
    }
}
