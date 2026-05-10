package com.wms.notification.domain.error;

/**
 * Thrown by an outbound channel adapter when the vendor returned a
 * non-retryable response (e.g., Slack 4xx — 404 channel-not-found,
 * 410 token-revoked).
 *
 * <p>Resilience4j {@code @Retry} on the adapter is configured to ignore
 * this exception via its {@code ignoreExceptions} list (see
 * {@code application.yml}), so the retry budget is preserved. The
 * delivery executor catches it and marks the {@code NotificationDelivery}
 * as terminally {@code FAILED} with outbox audit code
 * {@code FAILED_PERMANENT}.
 *
 * <p>{@code statusCode} is optional metadata — adapters with a numeric
 * vendor status code (HTTP) populate it; adapters without one (e.g.,
 * SDK-level malformed-payload) leave it as {@code -1}.
 */
public final class ChannelPermanentFailureException extends ChannelDispatchException {

    private final int statusCode;

    public ChannelPermanentFailureException(String message) {
        super(message);
        this.statusCode = -1;
    }

    public ChannelPermanentFailureException(int statusCode, String message) {
        super(message);
        this.statusCode = statusCode;
    }

    public ChannelPermanentFailureException(String message, Throwable cause) {
        super(message, cause);
        this.statusCode = -1;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public int statusCode() {
        return statusCode;
    }
}
