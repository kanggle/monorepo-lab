package com.example.notification.domain.exception;

/**
 * Raised when a client asks for the VAPID public key (to create a Web Push subscription)
 * but the server has no VAPID keypair configured — push is effectively disabled
 * (TASK-BE-464). Surfaces as HTTP 503 {@code PUSH_NOT_CONFIGURED}.
 */
public class PushNotConfiguredException extends RuntimeException {
    public PushNotConfiguredException() {
        super("Push notifications are not configured on this server");
    }
}
