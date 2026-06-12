package com.example.erp.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * External-channel delivery configuration (TASK-ERP-BE-020, v2.0). Everything is
 * <b>OFF by default = production net-zero</b>: with {@code enabled=false} no external
 * delivery is created and the retry scheduler bean is absent, so notification-service
 * behaves exactly as the v1 IN_APP-only path.
 *
 * <ul>
 *   <li>{@code enabled} — gates the dispatch-time creation of a PENDING external delivery
 *       (the consume use case only fans out to an external channel when this is true).</li>
 *   <li>{@code mode} — selects the external adapter ({@code slack} = real
 *       {@code SlackWebhookChannelAdapter}; anything else / unset = the no-op stub).</li>
 *   <li>{@code slack.*} — the real Slack-webhook adapter settings.</li>
 *   <li>{@code retry.*} — the {@code DeliveryRetryScheduler} cadence + Category C backoff.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "erpplatform.notification.external")
public class ExternalNotificationProperties {

    /** Master gate: create + drive external deliveries. Default false (net-zero). */
    private boolean enabled = false;

    /** Adapter selector: {@code slack} = real adapter; else the no-op stub. */
    private String mode = "noop";

    private final Slack slack = new Slack();
    private final Retry retry = new Retry();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    public Slack getSlack() {
        return slack;
    }

    public Retry getRetry() {
        return retry;
    }

    /** Real Slack-webhook adapter settings. */
    public static class Slack {
        /** Incoming-webhook URL (e.g. https://hooks.slack.com/services/...). */
        private String webhookUrl = "";
        private int connectTimeoutMs = 2000;
        private int readTimeoutMs = 5000;

        public String getWebhookUrl() {
            return webhookUrl;
        }

        public void setWebhookUrl(String webhookUrl) {
            this.webhookUrl = webhookUrl;
        }

        public int getConnectTimeoutMs() {
            return connectTimeoutMs;
        }

        public void setConnectTimeoutMs(int connectTimeoutMs) {
            this.connectTimeoutMs = connectTimeoutMs;
        }

        public int getReadTimeoutMs() {
            return readTimeoutMs;
        }

        public void setReadTimeoutMs(int readTimeoutMs) {
            this.readTimeoutMs = readTimeoutMs;
        }
    }

    /** {@code DeliveryRetryScheduler} cadence + Category C backoff (ADR-MONO-005 § D5). */
    public static class Retry {
        /** Run the scheduler. Default false (net-zero). */
        private boolean enabled = false;
        /** Fixed delay between polls (ms). */
        private long pollIntervalMs = 5000;
        /** Max due deliveries processed per tick. */
        private int batchSize = 50;
        /** First-retry backoff base (ms); grows exponentially. */
        private long initialBackoffMs = 1000;
        /** Backoff ceiling (ms). */
        private long maxBackoffMs = 60000;

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public long getPollIntervalMs() {
            return pollIntervalMs;
        }

        public void setPollIntervalMs(long pollIntervalMs) {
            this.pollIntervalMs = pollIntervalMs;
        }

        public int getBatchSize() {
            return batchSize;
        }

        public void setBatchSize(int batchSize) {
            this.batchSize = batchSize;
        }

        public long getInitialBackoffMs() {
            return initialBackoffMs;
        }

        public void setInitialBackoffMs(long initialBackoffMs) {
            this.initialBackoffMs = initialBackoffMs;
        }

        public long getMaxBackoffMs() {
            return maxBackoffMs;
        }

        public void setMaxBackoffMs(long maxBackoffMs) {
            this.maxBackoffMs = maxBackoffMs;
        }
    }
}
