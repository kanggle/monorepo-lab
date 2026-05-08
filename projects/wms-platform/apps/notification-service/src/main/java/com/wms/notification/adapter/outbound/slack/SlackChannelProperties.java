package com.wms.notification.adapter.outbound.slack;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds {@code wms.notification.channels.slack.<alias>.webhook-url} to a map.
 *
 * <p>Empty / missing values are kept as-is — {@link SlackChannelAdapter}
 * raises {@link ChannelNotConfiguredException} when an alias is requested
 * but its webhook URL is blank, so the dev fail-closed contract is enforced
 * at call-time (not at startup).
 */
@ConfigurationProperties(prefix = "wms.notification.channels")
public class SlackChannelProperties {

    private final Map<String, ChannelConfig> slack = new HashMap<>();

    public Map<String, ChannelConfig> getSlack() {
        return slack;
    }

    /** Resolves a Slack alias (e.g. {@code wms-alerts}) to a webhook URL. */
    public String webhookUrlFor(String channelAlias) {
        ChannelConfig cfg = slack.get(channelAlias);
        if (cfg == null) {
            return null;
        }
        return cfg.getWebhookUrl();
    }

    public static class ChannelConfig {
        private String webhookUrl;

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }
    }
}
