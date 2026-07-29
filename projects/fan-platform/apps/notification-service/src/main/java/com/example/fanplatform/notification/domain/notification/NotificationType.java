package com.example.fanplatform.notification.domain.notification;

/**
 * The kind of notification, derived from the consumed event.
 *
 * <p><b>Membership lifecycle</b> (one event type → exactly one notification type):
 * <ul>
 *   <li>{@code fan.membership.activated} → {@link #WELCOME}</li>
 *   <li>{@code fan.membership.canceled}  → {@link #CANCELLATION}</li>
 *   <li>{@code fan.membership.expired}   → {@link #EXPIRY_REMINDER}</li>
 * </ul>
 *
 * <p><b>Community interaction</b> (TASK-FAN-BE-026):
 * <ul>
 *   <li>{@code community.reaction.added} → {@link #REACTION_BADGE} — unambiguous.</li>
 *   <li>{@code community.comment.added}  → {@link #REPLY} <em>or</em> {@link #MENTION},
 *       <b>depending on the recipient's role</b> in the event (post author vs. a
 *       mentioned account). A single event can produce both. The event type alone
 *       therefore does NOT determine the notification type, so
 *       {@link #fromEventType(String)} deliberately rejects it — the use case
 *       selects the constant per recipient.</li>
 * </ul>
 *
 * <p>{@code EXPIRY_REMINDER} was added by TASK-FAN-BE-014; the three community
 * values by TASK-FAN-BE-026. Each addition needs a migration extending the
 * {@code ck_notification_type} CHECK allow-list (V2 / V3).
 */
public enum NotificationType {
    WELCOME,
    CANCELLATION,
    EXPIRY_REMINDER,
    REPLY,
    MENTION,
    REACTION_BADGE;

    public static final String EVENT_ACTIVATED = "fan.membership.activated";
    public static final String EVENT_CANCELED = "fan.membership.canceled";
    public static final String EVENT_EXPIRED = "fan.membership.expired";
    public static final String EVENT_COMMENT_ADDED = "community.comment.added";
    public static final String EVENT_REACTION_ADDED = "community.reaction.added";

    /**
     * Maps an envelope {@code eventType} to its notification type, for the event
     * types where that mapping is 1:1.
     *
     * @throws IllegalArgumentException for an unsupported / unknown event type, and
     *         for {@code community.comment.added} whose notification type is
     *         recipient-role-dependent (REPLY vs MENTION) — the consumer treats an
     *         unmapped event type as a non-retryable failure → DLQ.
     */
    public static NotificationType fromEventType(String eventType) {
        return switch (eventType) {
            case EVENT_ACTIVATED -> WELCOME;
            case EVENT_CANCELED -> CANCELLATION;
            case EVENT_EXPIRED -> EXPIRY_REMINDER;
            case EVENT_REACTION_ADDED -> REACTION_BADGE;
            case EVENT_COMMENT_ADDED -> throw new IllegalArgumentException(
                    "Event type " + EVENT_COMMENT_ADDED + " maps to REPLY or MENTION "
                            + "depending on the recipient's role; select the constant per recipient");
            default -> throw new IllegalArgumentException(
                    "Unsupported event type: " + eventType);
        };
    }
}
