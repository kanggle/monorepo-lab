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
 *   <li>{@link NotificationType#REPLY} — "New reply on your post" + who commented
 *       and on which post (TASK-FAN-BE-026).</li>
 *   <li>{@link NotificationType#MENTION} — "You were mentioned in a comment".</li>
 *   <li>{@link NotificationType#REACTION_BADGE} — "Someone reacted to your post"
 *       + the reaction type.</li>
 * </ul>
 *
 * <p>The community renderers name the actor by {@code accountId} only: the event
 * carries no display name, and notification-service is forbidden from reading
 * another service's tables or calling it synchronously (architecture.md
 * § Forbidden dependencies) — so a display name would require a contract change,
 * not a lookup.
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

    /**
     * Renders the REPLY content for a {@code community.comment.added} payload —
     * addressed to the post's author.
     */
    public static RenderedContent reply(String commenterAccountId, String postId) {
        String title = "New reply on your post";
        String body = commenterAccountId + " commented on your post " + postId + ".";
        return new RenderedContent(title, body);
    }

    /**
     * Renders the MENTION content for a {@code community.comment.added} payload —
     * addressed to one mentioned account.
     */
    public static RenderedContent mention(String commenterAccountId, String postId) {
        String title = "You were mentioned in a comment";
        String body = commenterAccountId + " mentioned you in a comment on post "
                + postId + ".";
        return new RenderedContent(title, body);
    }

    /**
     * Renders the REACTION_BADGE content for a {@code community.reaction.added}
     * payload — addressed to the post's author.
     */
    public static RenderedContent reactionBadge(String reactorAccountId, String reactionType,
                                                String postId) {
        String title = "Someone reacted to your post";
        String body = reactorAccountId + " reacted " + reactionType
                + " to your post " + postId + ".";
        return new RenderedContent(title, body);
    }
}
