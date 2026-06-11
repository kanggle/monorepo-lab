package com.example.fanplatform.notification.domain.notification;

import java.time.Instant;

/**
 * Renders the {@code title} + {@code body} of a notification from the consumed
 * event's payload (architecture.md § Event → Notification mapping). Pure domain
 * — no Spring, no JPA; deterministic given the same inputs (so the template unit
 * test is stable).
 *
 * <ul>
 *   <li>{@link NotificationType#WELCOME} — "Welcome to {tier} membership" + the
 *       validity window + plan length.</li>
 *   <li>{@link NotificationType#CANCELLATION} — "Your {tier} membership was
 *       canceled" + the cancel time + optional reason.</li>
 *   <li>{@link NotificationType#EXPIRY_REMINDER} — "Your {tier} membership has
 *       expired" + the window end ({@code validTo}).</li>
 * </ul>
 */
public final class NotificationTemplate {

    private NotificationTemplate() {
    }

    /** The rendered presentation content of a notification. */
    public record RenderedContent(String title, String body) {
    }

    /** Renders the WELCOME content for an {@code activated} event payload. */
    public static RenderedContent welcome(String tier, int planMonths,
                                          Instant validFrom, Instant validTo) {
        String title = "Welcome to " + tier + " membership";
        String body = "Your " + tier + " membership is active from " + validFrom
                + " to " + validTo + " (" + planMonths + " month"
                + (planMonths == 1 ? "" : "s") + ").";
        return new RenderedContent(title, body);
    }

    /** Renders the CANCELLATION content for a {@code canceled} event payload. */
    public static RenderedContent cancellation(String tier, Instant canceledAt, String reason) {
        String title = "Your " + tier + " membership was canceled";
        StringBuilder body = new StringBuilder("Your ")
                .append(tier)
                .append(" membership was canceled at ")
                .append(canceledAt)
                .append('.');
        if (reason != null && !reason.isBlank()) {
            body.append(" Reason: ").append(reason).append('.');
        }
        return new RenderedContent(title, body.toString());
    }

    /** Renders the EXPIRY_REMINDER content for an {@code expired} event payload. */
    public static RenderedContent expiry(String tier, Instant validTo) {
        String title = "Your " + tier + " membership has expired";
        String body = "Your " + tier + " membership ended on " + validTo
                + ". Renew to keep your member benefits.";
        return new RenderedContent(title, body);
    }
}
