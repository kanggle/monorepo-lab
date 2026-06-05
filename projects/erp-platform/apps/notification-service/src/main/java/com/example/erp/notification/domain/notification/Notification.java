package com.example.erp.notification.domain.notification;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * In-app notification aggregate (pure domain — no framework). Created only by
 * consuming an approval transition event (notifications are never created via
 * REST). One row per resolved recipient.
 *
 * <p>The {@code read} flag is the single mutable field (an in-app read receipt
 * is UI state, not an audit fact); {@code readAt} is set on the first mark-read
 * and is idempotent thereafter (re-marking preserves the original timestamp —
 * notification-api.md § POST {@code …/read}).
 */
public final class Notification {

    private final String id;
    private final String tenantId;
    private final String recipientId;
    private final NotificationType type;
    private final String title;
    private final String body;
    private final SourceRef source;
    private final Instant createdAt;

    private boolean read;
    private Instant readAt;

    /** Reconstruction constructor (persistence adapter + factory). */
    public Notification(String id,
                        String tenantId,
                        String recipientId,
                        NotificationType type,
                        String title,
                        String body,
                        SourceRef source,
                        boolean read,
                        Instant createdAt,
                        Instant readAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.tenantId = Objects.requireNonNull(tenantId, "tenantId");
        this.recipientId = Objects.requireNonNull(recipientId, "recipientId");
        this.type = Objects.requireNonNull(type, "type");
        this.title = Objects.requireNonNull(title, "title");
        this.body = Objects.requireNonNull(body, "body");
        this.source = Objects.requireNonNull(source, "source");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.read = read;
        this.readAt = readAt;
        if (read && readAt == null) {
            throw new IllegalArgumentException("read notification must carry readAt");
        }
        if (!read && readAt != null) {
            throw new IllegalArgumentException("unread notification must not carry readAt");
        }
    }

    /** Factory for a fresh, unread notification. */
    public static Notification create(String id,
                                      String tenantId,
                                      String recipientId,
                                      NotificationType type,
                                      String title,
                                      String body,
                                      SourceRef source,
                                      Instant createdAt) {
        return new Notification(id, tenantId, recipientId, type, title, body, source,
                false, createdAt, null);
    }

    /**
     * Marks the notification read. Idempotent: the first call sets
     * {@code read = true} + {@code readAt = now}; a subsequent call is a no-op
     * that preserves the original {@code readAt} (notification-api.md
     * § mark-read).
     */
    public void markRead(Instant now) {
        if (read) {
            return;
        }
        this.read = true;
        this.readAt = Objects.requireNonNull(now, "now");
    }

    public String id() { return id; }
    public String tenantId() { return tenantId; }
    public String recipientId() { return recipientId; }
    public NotificationType type() { return type; }
    public String title() { return title; }
    public String body() { return body; }
    public SourceRef source() { return source; }
    public boolean read() { return read; }
    public Instant createdAt() { return createdAt; }
    public Optional<Instant> readAt() { return Optional.ofNullable(readAt); }
}
