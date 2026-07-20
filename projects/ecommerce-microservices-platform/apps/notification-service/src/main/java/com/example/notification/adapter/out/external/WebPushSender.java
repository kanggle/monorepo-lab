package com.example.notification.adapter.out.external;

import com.example.notification.application.port.out.NotificationSender;
import com.example.notification.application.port.out.PushSubscriptionRepository;
import com.example.notification.application.port.out.WebPushGateway;
import com.example.notification.application.port.out.WebPushSendResult;
import com.example.notification.domain.model.NotificationChannel;
import com.example.notification.domain.model.PushSubscription;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Outbound adapter for the {@link NotificationChannel#PUSH} channel (TASK-BE-464), replacing the
 * BE-463 stub. The {@code recipient} passed by {@code NotificationSendService} is the user id;
 * this resolves that user's active Web Push subscriptions and delivers the rendered message to
 * each via {@link WebPushGateway}. Dead subscriptions (404/410) are pruned so they are not retried.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WebPushSender implements NotificationSender {

    private final PushSubscriptionRepository subscriptionRepository;
    private final WebPushGateway webPushGateway;
    private final ObjectMapper objectMapper;

    @Override
    public void send(String recipient, String subject, String body) {
        if (!webPushGateway.isConfigured()) {
            log.warn("Web Push not configured; skipping push for user {}", recipient);
            return;
        }

        List<PushSubscription> subscriptions = subscriptionRepository.findByUserId(recipient);
        if (subscriptions.isEmpty()) {
            log.debug("No push subscriptions for user {}; nothing to deliver", recipient);
            return;
        }

        byte[] payload = toPayload(subject, body);
        int delivered = 0;
        int failed = 0;
        for (PushSubscription subscription : subscriptions) {
            switch (deliver(subscription, payload)) {
                case DELIVERED -> delivered++;
                case FAILED -> failed++;
                case PRUNED -> { /* housekeeping, neither a delivery nor an outage */ }
            }
        }

        // TASK-BE-533 — the per-subscription swallow in deliver() is deliberate (one dead endpoint
        // must not abort the user's other subscriptions), but before this escalation it also meant
        // a push that reached NOBODY returned normally: NotificationSendService marked the row SENT
        // and no failure was ever counted, so instrumenting only the service layer would have left
        // push failures invisible in a new way. Escalate only when every attempt failed — a partial
        // success is still a delivery, and an expired-and-pruned subscription is housekeeping.
        if (failed > 0 && delivered == 0) {
            throw new WebPushDeliveryException(
                    "Push delivery failed for all " + failed + " subscription(s) of user " + recipient, null);
        }
    }

    private enum DeliveryOutcome { DELIVERED, FAILED, PRUNED }

    private DeliveryOutcome deliver(PushSubscription subscription, byte[] payload) {
        try {
            WebPushSendResult result = webPushGateway.send(subscription, payload);
            if (result.isExpired()) {
                subscriptionRepository.delete(subscription);
                log.info("Pruned expired push subscription (status {}) for user {}",
                        result.statusCode(), subscription.getUserId());
                return DeliveryOutcome.PRUNED;
            } else if (!result.isSuccess()) {
                log.warn("Push delivery returned status {} for user {}",
                        result.statusCode(), subscription.getUserId());
                return DeliveryOutcome.FAILED;
            }
            log.info("Push notification delivered to user {}", subscription.getUserId());
            return DeliveryOutcome.DELIVERED;
        } catch (Exception e) {
            // One dead endpoint must not abort delivery to the user's other subscriptions.
            log.error("Push delivery failed for user {}: {}", subscription.getUserId(), e.getMessage());
            return DeliveryOutcome.FAILED;
        }
    }

    private byte[] toPayload(String subject, String body) {
        try {
            return objectMapper.writeValueAsBytes(Map.of(
                    "title", subject == null ? "" : subject,
                    "body", body == null ? "" : body));
        } catch (JsonProcessingException e) {
            throw new WebPushDeliveryException("Failed to serialize push payload", e);
        }
    }

    @Override
    public NotificationChannel supportedChannel() {
        return NotificationChannel.PUSH;
    }
}
