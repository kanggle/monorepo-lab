package com.example.erp.notification.infrastructure.channel;

import com.example.common.resilience.ResilienceClientFactory;
import com.example.erp.notification.application.port.outbound.NotificationChannelPort;
import com.example.erp.notification.config.ExternalNotificationProperties;
import com.example.erp.notification.domain.delivery.DeliveryChannel;
import com.example.erp.notification.domain.notification.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Real external channel (TASK-ERP-BE-020, v2.0): POSTs a rendered message to a Slack
 * incoming webhook. Active only when {@code erpplatform.notification.external.mode=slack}
 * (else the {@link NoopExternalChannelAdapter} is the default {@code SLACK} bean) — exactly
 * one {@code SLACK} {@link NotificationChannelPort} per mode.
 *
 * <p><b>Best-effort, never throws, green-wash-safe</b>: reports
 * {@link DeliveryOutcome#ofDelivered()} <b>only</b> on a 2xx; any non-2xx / transport error
 * is caught and reported as {@link DeliveryOutcome#failed(String)} (NOT delivered), so the
 * {@code DeliveryRetryScheduler} retries it through the Category C budget rather than the
 * delivery being falsely marked delivered. The multi-attempt retry budget is owned by the
 * scheduler (this adapter performs a single attempt per call).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "erpplatform.notification.external.mode", havingValue = "slack")
public class SlackWebhookChannelAdapter implements NotificationChannelPort {

    private final RestClient restClient;

    public SlackWebhookChannelAdapter(ExternalNotificationProperties properties) {
        ExternalNotificationProperties.Slack slack = properties.getSlack();
        this.restClient = ResilienceClientFactory.buildRestClient(
                slack.getWebhookUrl(), slack.getConnectTimeoutMs(), slack.getReadTimeoutMs());
    }

    @Override
    public DeliveryChannel channel() {
        return DeliveryChannel.SLACK;
    }

    @Override
    public DeliveryOutcome deliver(Notification notification) {
        try {
            restClient.post()
                    .uri("")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", render(notification)))
                    .retrieve()
                    .toBodilessEntity();
            return DeliveryOutcome.ofDelivered();
        } catch (Exception e) {
            log.warn("Slack webhook delivery failed for notification id={}: {}",
                    notification.id(), e.getMessage());
            return DeliveryOutcome.failed("slack webhook delivery failed: " + e.getMessage());
        }
    }

    private static String render(Notification n) {
        return "[ERP] " + n.title() + "\n" + n.body() + "\n(recipient: " + n.recipientId() + ")";
    }
}
