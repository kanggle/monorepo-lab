package com.example.fanplatform.notification.domain.notification;

/**
 * Read state of a notification. The only mutable state on the aggregate:
 * {@code UNREAD → READ} (one-way; mark-read is idempotent).
 */
public enum NotificationStatus {
    UNREAD,
    READ
}
