package com.wms.notification.domain.routing;

import java.util.Objects;

/**
 * One channel destination resolved by a routing rule. The combination of
 * {@code channelType} and {@code channelId} maps the abstract target to a
 * vendor-specific endpoint (Slack webhook URL, email address group, push topic).
 *
 * @param channelType discriminator — selects the {@code ChannelPort} adapter
 * @param channelId   logical alias resolved at adapter time (e.g.
 *                    {@code wms-alerts} → Slack webhook URL via env var)
 * @param templateKey reference to a hard-coded template in v1; v2 = lookup
 */
public record ChannelTarget(ChannelType channelType, String channelId, String templateKey) {

    public ChannelTarget {
        Objects.requireNonNull(channelType, "channelType");
        Objects.requireNonNull(channelId, "channelId");
        Objects.requireNonNull(templateKey, "templateKey");
        if (channelId.isBlank()) {
            throw new IllegalArgumentException("channelId must not be blank");
        }
        if (templateKey.isBlank()) {
            throw new IllegalArgumentException("templateKey must not be blank");
        }
    }
}
