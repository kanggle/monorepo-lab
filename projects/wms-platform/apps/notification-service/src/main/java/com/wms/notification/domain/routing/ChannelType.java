package com.wms.notification.domain.routing;

/**
 * Supported channel types. v1: {@code SLACK} only. v2: email / push / SMS.
 *
 * <p>Adding a new channel type = new {@code ChannelPort} impl + new enum
 * value. Domain shape unchanged (architecture.md § Extensibility Notes).
 */
public enum ChannelType {
    SLACK
}
