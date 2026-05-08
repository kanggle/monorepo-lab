package com.wms.notification.adapter.outbound.slack;

/**
 * Thrown by {@link SlackChannelAdapter} when the requested channel alias
 * has no configured webhook URL (env var unset or blank).
 *
 * <p>Treated as a permanent failure — the delivery transitions to
 * {@code FAILED} without consuming retry budget. Operators must inject the
 * webhook URL and re-drive the delivery manually (v1) or via the admin
 * retry API (v2). Per spec edge case #1: <em>fail-closed in dev</em>.
 */
public class ChannelNotConfiguredException extends RuntimeException {

    private final String channelAlias;

    public ChannelNotConfiguredException(String channelAlias) {
        super("Slack webhook URL not configured for channel alias: " + channelAlias);
        this.channelAlias = channelAlias;
    }

    public String channelAlias() {
        return channelAlias;
    }
}
