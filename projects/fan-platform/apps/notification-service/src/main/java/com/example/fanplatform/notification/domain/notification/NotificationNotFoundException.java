package com.example.fanplatform.notification.domain.notification;

/**
 * Raised when the caller requests a notification id that does not exist OR
 * belongs to another account / tenant. Mapped to 404 {@code NOTIFICATION_NOT_FOUND}
 * (no existence leak — a foreign-account id is indistinguishable from a
 * non-existent one).
 */
public class NotificationNotFoundException extends RuntimeException {

    public NotificationNotFoundException(String id) {
        super("Notification not found: " + id);
    }
}
