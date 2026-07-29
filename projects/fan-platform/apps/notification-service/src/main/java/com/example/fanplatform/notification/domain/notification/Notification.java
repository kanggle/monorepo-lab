package com.example.fanplatform.notification.domain.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Notification aggregate — one in-app notification held for one fan account,
 * derived from one consumed event: a membership lifecycle event (WELCOME /
 * CANCELLATION / EXPIRY_REMINDER, correlated by {@code membershipId}) or a
 * community interaction event (REPLY / MENTION / REACTION_BADGE, correlated by
 * {@code postId} — TASK-FAN-BE-026).
 *
 * <p>The single mutable state is {@code status} ({@code UNREAD → READ}) via
 * {@link #markRead(Instant)} (idempotent). A notification is created ONLY by the
 * event consumer; there is no notification-creating REST endpoint. The
 * {@code domain} layer depends only on {@code jakarta.persistence} (the
 * pragmatic JPA exception, matching the membership / community convention) — no
 * Spring imports.
 *
 * <p>{@code (sourceEventId, accountId, type)} is unique — the secondary idempotency
 * guard behind the {@code processed_events} table. It is a composite (not
 * {@code sourceEventId} alone) because one {@code community.comment.added} event
 * can legitimately fan out to several recipients (a REPLY plus one MENTION per
 * mentioned account); a duplicate delivery still collides because it regenerates
 * the identical tuples (V3 migration).
 */
@Entity
@Table(name = "notifications")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification {

    @Id
    @Column(name = "id", length = 36, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 64, nullable = false)
    private String tenantId;

    @Column(name = "account_id", length = 36, nullable = false)
    private String accountId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", length = 32, nullable = false)
    private NotificationType type;

    @Column(name = "title", length = 256, nullable = false)
    private String title;

    @Column(name = "body", length = 2000, nullable = false)
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 16, nullable = false)
    private NotificationStatus status;

    @Column(name = "source_event_id", length = 64, nullable = false)
    private String sourceEventId;

    @Column(name = "source_event_type", length = 64, nullable = false)
    private String sourceEventType;

    /**
     * The originating membership aggregate id — non-null for a membership-sourced
     * notification (WELCOME / CANCELLATION / EXPIRY_REMINDER), {@code null} for a
     * community-sourced one (V3 dropped the NOT NULL).
     */
    @Column(name = "membership_id", length = 36)
    private String membershipId;

    /**
     * The correlating post id — non-null for a community-sourced notification
     * (REPLY / MENTION / REACTION_BADGE), {@code null} for a membership-sourced one.
     */
    @Column(name = "post_id", length = 36)
    private String postId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Factory for a brand-new UNREAD notification. {@code createdAt} MUST already
     * be truncated to micros by the caller (§15) so an in-memory value equals the
     * DB re-read.
     *
     * <p>{@code membershipId} and {@code postId} are the two mutually-alternative
     * origin correlations: a membership-sourced notification passes a
     * {@code membershipId} and {@code null} post, a community-sourced one passes
     * {@code null} membership and a {@code postId}. Which one is non-null is the
     * calling use case's decision; the schema does not enforce the exclusivity.
     */
    public static Notification create(String id, String tenantId, String accountId,
                                      NotificationType type, String title, String body,
                                      String sourceEventId, String sourceEventType,
                                      String membershipId, String postId, Instant createdAt) {
        Notification n = new Notification();
        n.id = id;
        n.tenantId = tenantId;
        n.accountId = accountId;
        n.type = type;
        n.title = title;
        n.body = body;
        n.status = NotificationStatus.UNREAD;
        n.sourceEventId = sourceEventId;
        n.sourceEventType = sourceEventType;
        n.membershipId = membershipId;
        n.postId = postId;
        n.createdAt = createdAt;
        n.readAt = null;
        return n;
    }

    /**
     * Marks the notification READ. The first call sets {@code status = READ} +
     * {@code readAt = now}; a re-mark of an already-READ notification is an
     * idempotent no-op that preserves the original {@code readAt}.
     */
    public void markRead(Instant now) {
        if (this.status == NotificationStatus.UNREAD) {
            this.status = NotificationStatus.READ;
            this.readAt = now;
        }
    }

    public boolean isRead() {
        return this.status == NotificationStatus.READ;
    }
}
