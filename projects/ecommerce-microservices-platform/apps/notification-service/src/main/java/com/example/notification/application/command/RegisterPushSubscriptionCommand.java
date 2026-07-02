package com.example.notification.application.command;

/**
 * Register/refresh a Web Push subscription for {@code userId} (TASK-BE-464). The keys are the
 * browser's W3C PushSubscription material. {@code expirationTime} is accepted from the client
 * for contract fidelity but not persisted (informational, frequently null).
 */
public record RegisterPushSubscriptionCommand(
        String userId,
        String endpoint,
        Long expirationTime,
        String p256dh,
        String auth
) {}
