package com.wms.notification.adapter.outbound.slack;

/**
 * Slack returned a 4xx response (404 channel-not-found, 410 token-revoked,
 * etc.). Permanent — Resilience4j {@code @Retry} ignores this exception
 * via its {@code ignoreExceptions} list (per application.yml).
 */
public class SlackPermanentFailureException extends RuntimeException {

    private final int statusCode;

    public SlackPermanentFailureException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
