package com.example.notification.application.port.out;

/**
 * Outcome of one Web Push delivery attempt (TASK-BE-464): the HTTP status the push service
 * returned for a single subscription endpoint.
 */
public record WebPushSendResult(int statusCode) {

    public boolean isSuccess() {
        return statusCode >= 200 && statusCode < 300;
    }

    /**
     * The push service reports the subscription is gone (404 Not Found / 410 Gone) — the
     * endpoint is dead and should be pruned so it is not retried on every send.
     */
    public boolean isExpired() {
        return statusCode == 404 || statusCode == 410;
    }
}
