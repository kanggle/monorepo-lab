package com.wms.notification.domain.error;

/**
 * Thrown by an outbound channel adapter when the requested channel alias
 * has no configured vendor endpoint (e.g., Slack webhook URL env var
 * unset or blank).
 *
 * <p>Treated as a permanent failure — the delivery transitions to
 * {@code FAILED} without consuming retry budget. Operators must inject
 * the channel configuration and re-drive the delivery manually (v1) or
 * via the admin retry API (v2). Per architecture spec edge case #1:
 * <em>fail-closed in dev</em>.
 */
public final class ChannelNotConfiguredException extends ChannelDispatchException {

    public ChannelNotConfiguredException(String message) {
        super(message);
    }
}
