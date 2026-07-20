package com.example.notification.domain.model;

/**
 * Bounded failure-reason vocabulary for the {@code notification_failed_total{channel, reason}}
 * counter (TASK-BE-533 / ADR-006 notification-service Scenario B).
 *
 * <p>The label set is deliberately an enum rather than the exception's message or class name:
 * a raw message would be unbounded and blow up Prometheus series cardinality (one series per
 * distinct SMTP error string). Every unrecognised failure collapses into {@link #UNKNOWN}, so
 * the series count is capped at {@code channels x reasons} no matter what the senders throw.
 *
 * <p>Classification walks the cause chain by <em>simple class name</em> rather than importing
 * {@code org.springframework.mail.*} — this keeps the domain layer free of framework types while
 * still recognising the concrete exceptions the senders raise.
 */
public enum NotificationFailureReason {

    /** SMTP credentials rejected by the relay. */
    MAIL_AUTH("mail_auth"),

    /** SMTP transport failure (relay unreachable, message rejected, connection reset). */
    MAIL_SEND("mail_send"),

    /** Web Push delivery failed for every one of the user's subscriptions. */
    PUSH_DELIVERY("push_delivery"),

    /** The payload could not be serialised — a bug, not an outage. */
    SERIALIZATION("serialization"),

    /** The send did not complete within its deadline. */
    TIMEOUT("timeout"),

    /** Anything not recognised above. Keeps cardinality bounded. */
    UNKNOWN("unknown");

    private final String tag;

    NotificationFailureReason(String tag) {
        this.tag = tag;
    }

    /** The Prometheus label value. Lower snake_case per {@code platform/observability.md}. */
    public String tag() {
        return tag;
    }

    /**
     * Maps a thrown failure onto the bounded vocabulary, walking the cause chain so a wrapped
     * {@code MailAuthenticationException} is still classified as {@link #MAIL_AUTH}.
     */
    public static NotificationFailureReason classify(Throwable throwable) {
        for (Throwable t = throwable; t != null && t != t.getCause(); t = t.getCause()) {
            NotificationFailureReason reason = ofType(t.getClass().getSimpleName());
            if (reason != null) {
                return reason;
            }
        }
        return UNKNOWN;
    }

    private static NotificationFailureReason ofType(String simpleName) {
        return switch (simpleName) {
            case "MailAuthenticationException", "AuthenticationFailedException" -> MAIL_AUTH;
            case "MailSendException", "MailParseException", "MailPreparationException",
                 "MessagingException", "SendFailedException" -> MAIL_SEND;
            case "WebPushDeliveryException" -> PUSH_DELIVERY;
            case "JsonProcessingException", "JsonMappingException" -> SERIALIZATION;
            case "TimeoutException", "SocketTimeoutException", "ConnectTimeoutException" -> TIMEOUT;
            default -> null;
        };
    }
}
