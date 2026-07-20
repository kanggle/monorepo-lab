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
        for (PushSubscription subscription : subscriptions) {
            deliver(subscription, payload);
        }
    }

    private void deliver(PushSubscription subscription, byte[] payload) {
        try {
            WebPushSendResult result = webPushGateway.send(subscription, payload);
            if (result.isExpired()) {
                subscriptionRepository.delete(subscription);
                log.info("Pruned expired push subscription (status {}) for user {}",
                        result.statusCode(), subscription.getUserId());
            } else if (!result.isSuccess()) {
                log.warn("Push delivery returned status {} for user {}",
                        result.statusCode(), subscription.getUserId());
            } else {
                log.info("Push notification delivered to user {}", subscription.getUserId());
            }
        } catch (Exception e) {
            // One dead endpoint must not abort delivery to the user's other subscriptions.
            log.error("Push delivery failed for user {}: {}", subscription.getUserId(), e.getMessage());
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
