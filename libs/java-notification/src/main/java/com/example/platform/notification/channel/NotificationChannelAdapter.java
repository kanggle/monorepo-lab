package com.example.platform.notification.channel;

/**
 * Service-Provider Interface toward an external notification channel vendor
 * (Slack, email/SMTP, FCM push, SMS, …). The shared delivery engine resolves an
 * adapter by its {@link #channel()} key and calls {@link #deliver(ChannelDeliveryRequest)};
 * the service supplies the concrete adapters (vendor SDK / HTTP wiring, timeouts,
 * circuit-breaker/retry, credential resolution).
 *
 * <p>This is the lift of the wms reference's sealed {@code ChannelPort} +
 * {@code SlackChannelAdapter} generalised to an open SPI: the lib owns the
 * abstraction; each domain registers its own channel set as Spring beans
 * (HARDSTOP-03 — no vendor credentials or service names in the lib).
 *
 * <p><b>Contract — never throw.</b> Unlike the wms reference (which threw typed
 * exceptions that the dispatcher caught), an adapter here encodes every outcome
 * in a {@link ChannelResult}: success, transient failure (retryable), or permanent
 * failure (do-not-retry). This keeps the engine's control flow exception-free and
 * forces every adapter to classify its own failures explicitly.
 */
public interface NotificationChannelAdapter {

    /**
     * The stable channel key this adapter handles (e.g. {@code "slack"},
     * {@code "email"}, {@code "fcm"}). Matched against
     * {@link com.example.platform.notification.delivery.DeliveryRecord#channel()}.
     */
    String channel();

    /**
     * Deliver one notification. MUST NOT throw — every outcome (success, transient
     * failure, permanent failure) is returned as a {@link ChannelResult}. An
     * unchecked exception escaping this method is a programming error; the engine
     * treats it defensively as a transient failure but adapters must not rely on it.
     *
     * @param request the fully-resolved delivery request
     * @return the classified outcome
     */
    ChannelResult deliver(ChannelDeliveryRequest request);
}
