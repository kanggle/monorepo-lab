package com.example.notification.adapter.out.external;

/**
 * A Web Push delivery attempt failed at the transport/crypto layer before any HTTP status
 * was obtained (TASK-BE-464). {@code WebPushSender} catches this per-subscription and logs
 * it; it is never surfaced to an HTTP client (the send path runs on a Kafka thread).
 */
public class WebPushDeliveryException extends RuntimeException {
    public WebPushDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
