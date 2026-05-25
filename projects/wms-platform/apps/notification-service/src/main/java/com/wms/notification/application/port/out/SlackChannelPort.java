package com.wms.notification.application.port.out;

/**
 * Marker for the Slack-specific {@link ChannelPort} — kept as a separate
 * type so the application service can request "send to a Slack channel"
 * without coupling to the {@code SlackChannelAdapter} implementation.
 */
public non-sealed interface SlackChannelPort extends ChannelPort {
}
