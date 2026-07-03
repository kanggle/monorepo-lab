package com.example.notification.application.command;

/**
 * Register/refresh a Web Push subscription for {@code userId} (TASK-BE-464). The keys are the
 * browser's W3C PushSubscription material. {@code expirationTime} is accepted from the client
 * for contract fidelity but not persisted (informational, frequently null).
 *
 * <p>{@code userAgent} is the request {@code User-Agent} header captured at register time
 * (TASK-FE-085) to label the device in the user's push-device list; it is optional (null when
 * the header is absent) and only applied when a NEW subscription row is created.
 */
public record RegisterPushSubscriptionCommand(
        String userId,
        String endpoint,
        Long expirationTime,
        String p256dh,
        String auth,
        String userAgent
) {}
