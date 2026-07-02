package com.example.notification.application.port.in;

import com.example.notification.application.command.RegisterPushSubscriptionCommand;
import com.example.notification.application.result.RegisterSubscriptionResult;
import com.example.notification.domain.model.PushSubscription;

import java.util.List;

/**
 * Inbound port for Web Push subscription management (TASK-BE-464) — the HTTP surface a
 * browser client uses to opt in/out of push and to fetch the server's VAPID public key.
 */
public interface ManagePushSubscriptionUseCase {

    /** Register a new subscription or refresh an existing endpoint's keys (idempotent on endpoint). */
    RegisterSubscriptionResult register(RegisterPushSubscriptionCommand command);

    /** Remove the given endpoint if it belongs to {@code userId}; no-op otherwise (idempotent). */
    void unregister(String userId, String endpoint);

    /**
     * All of {@code userId}'s push subscriptions (one per browser/device), newest first —
     * powers the push-device list UI (TASK-FE-085).
     */
    List<PushSubscription> listByUser(String userId);

    /**
     * The server's VAPID public key for {@code pushManager.subscribe({ applicationServerKey })}.
     * @throws com.example.notification.domain.exception.PushNotConfiguredException if unset.
     */
    String getVapidPublicKey();
}
