package com.example.notification.adapter.out.external;

import com.example.notification.application.port.out.NotificationSender;
import com.example.notification.domain.model.NotificationChannel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Outbound adapter for the {@link NotificationChannel#PUSH} channel.
 *
 * <p>This is a <strong>stub / log-only</strong> implementation, deliberately at the same
 * fidelity as {@link EmailNotificationSender} being a dev {@code JavaMailSender}. Registering
 * it as a {@link Component} makes it join the {@code List<NotificationSender>} injected into
 * {@code NotificationSendService}, which fills the {@code senderMap[PUSH]} slot — so PUSH
 * templates now flow through render → send instead of hitting the "No sender available for
 * channel PUSH" branch and being silently dropped (TASK-BE-463).
 *
 * <p>Actual delivery to a push provider (FCM / APNs for mobile banners, Web Push for browser
 * notifications) is out of scope here. Wiring a real provider requires a
 * {@code userId -> device token / web-push subscription} registry first, since — unlike email —
 * a push recipient is not an address but a per-device token. That registry plus the provider SDK
 * integration is a follow-up task; this class is the seam where that call replaces the log line.
 */
@Slf4j
@Component
public class PushNotificationSender implements NotificationSender {

    @Override
    public void send(String recipient, String subject, String body) {
        // No external call in the stub: the send path must not throw, or renderAndSend would
        // mark the notification FAILED. A real provider call (FCM/APNs/Web Push) goes here.
        log.info("Push notification dispatched (stub). recipient={}, subject={}", recipient, subject);
    }

    @Override
    public NotificationChannel supportedChannel() {
        return NotificationChannel.PUSH;
    }
}
