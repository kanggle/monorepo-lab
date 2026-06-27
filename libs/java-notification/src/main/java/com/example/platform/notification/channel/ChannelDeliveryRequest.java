package com.example.platform.notification.channel;

import java.util.Map;
import java.util.Objects;

/**
 * Immutable request handed to a {@link NotificationChannelAdapter} to deliver one
 * notification over one external channel (Slack, email, FCM, SMS, …).
 *
 * <p>The application service resolves everything domain-specific (the recipient
 * alias, the rendered title/body, any extra channel metadata) and hands the
 * adapter a fully-resolved request. The adapter resolves the alias to a vendor
 * endpoint and renders {@code payloadJson} into a vendor-specific shape.
 *
 * <p>Project-agnostic (HARDSTOP-03): carries no domain types, no service names,
 * no credentials — those are adapter/service concerns.
 *
 * @param recipient   logical recipient alias the adapter resolves to a vendor endpoint
 * @param title       short headline
 * @param body        human-readable detail
 * @param payloadJson serialised payload snapshot (the adapter renders the vendor body from it)
 * @param metadata    optional opaque key/value hints (never null — empty map if none)
 */
public record ChannelDeliveryRequest(
        String recipient,
        String title,
        String body,
        String payloadJson,
        Map<String, String> metadata
) {
    public ChannelDeliveryRequest {
        Objects.requireNonNull(recipient, "recipient");
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }

    /** Convenience factory for the common no-metadata case. */
    public static ChannelDeliveryRequest of(String recipient, String title, String body, String payloadJson) {
        return new ChannelDeliveryRequest(recipient, title, body, payloadJson, Map.of());
    }
}
