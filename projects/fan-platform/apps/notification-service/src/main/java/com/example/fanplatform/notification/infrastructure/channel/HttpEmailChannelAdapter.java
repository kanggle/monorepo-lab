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
 * Real EMAIL channel — a provider-agnostic HTTP transactional-email integration
 * (TASK-FAN-BE-016), the notification-service's first real external channel. It
 * re-implements {@link NotificationChannelPort} behind the same port the v1 mocks
 * use; the domain and use-case layers are unchanged (architecture.md § Channel Mock
 * Boundary).
 *
 * <p><b>Opt-in.</b> Active only when {@code fanplatform.notification.email.mode=http}.
 * The default {@code mock} mode keeps {@link LoggingEmailChannelAdapter} — exactly
 * one EMAIL {@link NotificationChannelPort} bean exists under either mode.
 *
 * <p><b>Best-effort, never-throw.</b> The use-case fans out across the channels
 * inside its {@code @Transactional} and the durable inbox row is authoritative and
 * <i>decoupled</i> from delivery. A real delivery failure (non-2xx, transport,
 * timeout, unparseable body) is therefore caught, recorded on the
 * {@code …{outcome=failed}} counter, logged {@code warn}, and returned as
 * {@code DeliveryResult(false, …)} — it MUST NOT throw, or a transient email outage
 * would roll back the in-app notification. (v1 had no automatic redelivery of a
 * failed real send; the inbox row remains the record — a redelivery mechanism is a
 * further increment.)
 *
 * <p><b>Recipient.</b> The consumed event carries no recipient email (only
 * {@code accountId} = IAM {@code sub}), and cross-service table reads are forbidden,
 * so the address is the deterministic synthetic {@code <accountId>@<recipient-domain>}
 * — a documented limitation (a production version would enrich via a preferences
 * lookup).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "fanplatform.notification.email.mode", havingValue = "http")
public class HttpEmailChannelAdapter implements NotificationChannelPort {

    static final String CHANNEL = "EMAIL";

    private final MeterRegistry meterRegistry;
    private final RestClient restClient;
    private final String apiKey;
    private final String fromAddress;
    private final String recipientDomain;

    public HttpEmailChannelAdapter(
            MeterRegistry meterRegistry,
            @Value("${fanplatform.notification.email.provider-base-url:}") String providerBaseUrl,
            @Value("${fanplatform.notification.email.api-key:}") String apiKey,
            @Value("${fanplatform.notification.email.from-address:no-reply@fan-platform.example}") String fromAddress,
            @Value("${fanplatform.notification.email.recipient-domain:fan-platform.example}") String recipientDomain,
            @Value("${fanplatform.notification.email.connect-timeout-ms:2000}") int connectTimeoutMs,
            @Value("${fanplatform.notification.email.read-timeout-ms:5000}") int readTimeoutMs) {
        this.meterRegistry = meterRegistry;
        this.apiKey = apiKey;
        this.fromAddress = fromAddress;
        this.recipientDomain = recipientDomain;
        this.restClient = ResilienceClientFactory.buildRestClient(
                providerBaseUrl, connectTimeoutMs, readTimeoutMs);
    }

    @Override
    public String channel() {
        return CHANNEL;
    }

    @Override
    public DeliveryResult deliver(Notification notification) {
        String recipient = notification.getAccountId() + "@" + recipientDomain;
        try {
            JsonNode response = restClient.post()
                    .uri("/emails")
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "from", fromAddress,
                            "to", recipient,
                            "subject", notification.getTitle(),
                            "body", notification.getBody()))
                    .retrieve()
                    .body(JsonNode.class);
            String ref = (response != null && response.hasNonNull("id"))
                    ? response.get("id").asText()
                    : null;
            if (ref == null || ref.isBlank()) {
                return failed(notification, recipient, "provider 2xx without a usable id");
            }
            ChannelDeliveryMetrics.delivered(meterRegistry, CHANNEL);
            log.info("[http-email] delivered notification id={} account={} type={} ref={}",
                    notification.getId(), notification.getAccountId(), notification.getType(), ref);
            return new DeliveryResult(true, CHANNEL, ref);
        } catch (Exception ex) {
            return failed(notification, recipient, ex.toString());
        }
    }

    private DeliveryResult failed(Notification notification, String recipient, String reason) {
        ChannelDeliveryMetrics.failed(meterRegistry, CHANNEL);
        log.warn("[http-email] delivery FAILED (best-effort; inbox row authoritative) "
                        + "id={} account={} to={} reason={}",
                notification.getId(), notification.getAccountId(), recipient, reason);
        return new DeliveryResult(false, CHANNEL, null);
    }
}
