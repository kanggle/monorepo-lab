package com.wms.notification.application.port.outbound;

import com.wms.notification.domain.routing.ChannelType;

/**
 * Sealed boundary toward an external channel vendor (Slack v1; email /
 * push v2). Adapters wrap the vendor SDK / HTTP call inside Resilience4j
 * circuit-breaker + retry.
 *
 * <p>Architecture rule: the application service hands the adapter a fully
 * resolved {@code recipient} alias and a serialised {@code payloadSnapshot};
 * the adapter is responsible for resolving the alias to a vendor endpoint
 * (Slack webhook URL via env var) and rendering the payload to a
 * vendor-specific shape.
 */
public sealed interface ChannelPort permits SlackChannelPort {

    /** Discriminator the {@code AlertRoutingService} matches against {@code ChannelTarget#channelType}. */
    ChannelType channelType();

    /**
     * Send the notification. Implementations should:
     *
     * <ol>
     *   <li>Honour their bounded HTTP timeouts (3s connect / 5s read for Slack).</li>
     *   <li>Translate non-retryable vendor responses (4xx, malformed) into
     *       a {@link com.wms.notification.domain.error.ChannelPermanentFailureException}
     *       so the caller doesn't waste retry budget.</li>
     *   <li>Translate retryable failures (5xx, IO, timeout) into a generic
     *       {@link RuntimeException} so Resilience4j's retry kicks in.</li>
     *   <li>Raise {@link com.wms.notification.domain.error.ChannelNotConfiguredException}
     *       if the channel alias has no configured vendor endpoint
     *       (fail-closed; treated as permanent at the application layer).</li>
     * </ol>
     *
     * @param recipient logical alias from {@link com.wms.notification.domain.routing.ChannelTarget#channelId()}
     * @param messageJson serialised vendor-shaped message body
     */
    void send(String recipient, String messageJson);
}
