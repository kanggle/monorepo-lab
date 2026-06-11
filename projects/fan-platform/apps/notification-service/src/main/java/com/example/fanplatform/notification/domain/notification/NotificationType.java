package com.example.fanplatform.notification.domain.notification;

/**
 * The kind of notification, derived from the consumed membership event type.
 *
 * <ul>
 *   <li>{@code fan.membership.activated} → {@link #WELCOME}</li>
 *   <li>{@code fan.membership.canceled}  → {@link #CANCELLATION}</li>
 *   <li>{@code fan.membership.expired}   → {@link #EXPIRY_REMINDER}</li>
 * </ul>
 *
 * <p>{@code EXPIRY_REMINDER} was added by TASK-FAN-BE-014 when the producer's
 * expiry sweeper began emitting {@code fan.membership.expired.v1}; a V2 migration
 * extends the {@code ck_notification_type} CHECK allow-list to match.
 */
public enum NotificationType {
    WELCOME,
    CANCELLATION,
    EXPIRY_REMINDER;

    public static final String EVENT_ACTIVATED = "fan.membership.activated";
    public static final String EVENT_CANCELED = "fan.membership.canceled";
    public static final String EVENT_EXPIRED = "fan.membership.expired";

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
            case EVENT_EXPIRED -> EXPIRY_REMINDER;
            default -> throw new IllegalArgumentException(
                    "Unsupported membership event type: " + eventType);
        };
    }
}
