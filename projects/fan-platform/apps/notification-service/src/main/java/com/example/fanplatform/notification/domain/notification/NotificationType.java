package com.example.fanplatform.notification.domain.notification;

/**
 * The kind of notification, derived from the consumed membership event type.
 *
 * <ul>
 *   <li>{@code fan.membership.activated} → {@link #WELCOME}</li>
 *   <li>{@code fan.membership.canceled}  → {@link #CANCELLATION}</li>
 * </ul>
 *
 * <p>{@code fan.membership.expired} is intentionally NOT mapped — the producer
 * does not emit it (read-time expiry, no sweeper), so this service does not
 * subscribe to it. A future increment that adds the sweeper would add an
 * {@code EXPIRY_REMINDER} type here (and a V2 migration to extend the
 * {@code ck_notification_type} allow-list).
 */
public enum NotificationType {
    WELCOME,
    CANCELLATION;

    public static final String EVENT_ACTIVATED = "fan.membership.activated";
    public static final String EVENT_CANCELED = "fan.membership.canceled";

    /**
     * Maps an envelope {@code eventType} to its notification type.
     *
     * @throws IllegalArgumentException for an unsupported / unknown event type —
     *         the consumer treats this as a non-retryable failure → DLQ.
     */
    public static NotificationType fromEventType(String eventType) {
        return switch (eventType) {
            case EVENT_ACTIVATED -> WELCOME;
            case EVENT_CANCELED -> CANCELLATION;
            default -> throw new IllegalArgumentException(
                    "Unsupported membership event type: " + eventType);
        };
    }
}
