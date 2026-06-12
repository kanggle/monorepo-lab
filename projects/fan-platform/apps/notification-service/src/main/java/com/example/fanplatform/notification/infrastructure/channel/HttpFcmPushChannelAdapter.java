package com.example.fanplatform.notification.infrastructure.channel;

import com.example.common.resilience.ResilienceClientFactory;
import com.example.fanplatform.notification.domain.channel.NotificationChannelPort;
import com.example.fanplatform.notification.domain.notification.Notification;
import com.fasterxml.jackson.databind.JsonNode;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

/**
 * Real PUSH channel — a Firebase Cloud Messaging (FCM) HTTP v1 integration
 * (TASK-FAN-BE-017), the symmetric completion of the real-channel story begun by the
 * EMAIL adapter ({@link HttpEmailChannelAdapter}, TASK-FAN-BE-016). It re-implements
 * {@link NotificationChannelPort} behind the same port the v1 mock uses; the domain
 * and use-case layers are unchanged (architecture.md § Channel Mock Boundary).
 *
 * <p><b>Opt-in.</b> Active only when {@code fanplatform.notification.push.mode=fcm}.
 * The default {@code mock} mode keeps {@link LoggingPushChannelAdapter} — exactly one
 * PUSH {@link NotificationChannelPort} bean exists under either mode (EMAIL channel
 * selection is independent).
 *
 * <p><b>Topic targeting.</b> The consumed event carries no device registration token
 * (only {@code accountId} = IAM {@code sub}) and cross-service reads are forbidden, so
 * the adapter uses FCM <b>topic</b> messaging ({@code message.topic =
 * <topic-prefix><accountId>}) — clients subscribe to their own per-account topic, so
 * no device-token registry is needed. Device-token targeting would require a device
 * registry / preferences lookup (out of scope).
 *
 * <p><b>Auth.</b> {@code Authorization: Bearer <api-key>}. Real FCM v1 mints a
 * short-lived OAuth2 access token from a Google service account; that token-minting is
 * out of scope — a real deployment supplies a current access token. No Google SDK is
 * added (plain RestClient).
 *
 * <p><b>Best-effort, never-throw</b> (same discipline as {@link HttpEmailChannelAdapter}):
 * any non-2xx / transport / timeout / unparseable-or-no-{@code name} response is caught,
 * recorded on {@code …{outcome=failed}}, logged {@code warn}, and returned as
 * {@code DeliveryResult(false, …)} — the fan-out runs inside the use-case
 * {@code @Transactional} and the durable inbox row is authoritative; a throw would roll
 * it back over a transient FCM outage.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "fanplatform.notification.push.mode", havingValue = "fcm")
public class HttpFcmPushChannelAdapter implements NotificationChannelPort {

    static final String CHANNEL = "PUSH";
    private static final String METRIC = "notification_channel_deliveries_total";

    private final MeterRegistry meterRegistry;
    private final RestClient restClient;
    private final String sendPath;
    private final String apiKey;
    private final String topicPrefix;

    public HttpFcmPushChannelAdapter(
            MeterRegistry meterRegistry,
            @Value("${fanplatform.notification.push.fcm-base-url:}") String fcmBaseUrl,
            @Value("${fanplatform.notification.push.project-id:}") String projectId,
            @Value("${fanplatform.notification.push.api-key:}") String apiKey,
            @Value("${fanplatform.notification.push.topic-prefix:fan_}") String topicPrefix,
            @Value("${fanplatform.notification.push.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${fanplatform.notification.push.read-timeout-ms:5000}") int readTimeoutMs) {
        this.meterRegistry = meterRegistry;
        this.apiKey = apiKey;
        this.topicPrefix = topicPrefix;
        this.sendPath = "/v1/projects/" + projectId + "/messages:send";
        this.restClient = ResilienceClientFactory.buildRestClient(
                fcmBaseUrl, connectTimeoutMs, readTimeoutMs);
    }

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public DeliveryResult deliver(Notification notification) {
        String topic = topicPrefix + sanitizeTopic(notification.getAccountId());
        try {
            JsonNode response = restClient.post()
                    .uri(sendPath)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("message", Map.of(
                            "topic", topic,
                            "notification", Map.of(
                                    "title", notification.getTitle(),
                                    "body", notification.getBody()))))
                    .retrieve()
                    .body(JsonNode.class);
            String ref = (response != null && response.hasNonNull("name"))
                    ? response.get("name").asText()
                    : null;
            if (ref == null || ref.isBlank()) {
                return failed(notification, topic, "FCM 2xx without a message name");
            }
            meterRegistry.counter(METRIC, "channel", CHANNEL, "outcome", "delivered").increment();
            log.info("[fcm-push] delivered notification id={} account={} type={} topic={} ref={}",
                    notification.getId(), notification.getAccountId(), notification.getType(), topic, ref);
            return new DeliveryResult(true, CHANNEL, ref);
        } catch (Exception ex) {
            return failed(notification, topic, ex.toString());
        }
    }

    private DeliveryResult failed(Notification notification, String topic, String reason) {
        meterRegistry.counter(METRIC, "channel", CHANNEL, "outcome", "failed").increment();
        log.warn("[fcm-push] delivery FAILED (best-effort; inbox row authoritative) "
                        + "id={} account={} topic={} reason={}",
                notification.getId(), notification.getAccountId(), topic, reason);
        return new DeliveryResult(false, CHANNEL, null);
    }

    /** Coerce an account id into the FCM topic charset {@code [a-zA-Z0-9-_.~%]+}. */
    private static String sanitizeTopic(String accountId) {
        return accountId == null ? "" : accountId.replaceAll("[^a-zA-Z0-9\\-_.~%]", "_");
    }
}
