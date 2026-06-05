package com.example.erp.notification.domain.error;

/**
 * Raised when an inbox detail / mark-read targets an id that is not visible to
 * the caller — an unknown id, <b>or</b> a notification owned by another
 * recipient. Maps to 404 {@code NOTIFICATION_NOT_FOUND} (not 403) so a
 * foreign-recipient row is indistinguishable from a non-existent one (no
 * enumeration oracle — notification-api.md § Errors, E6 recipient scope).
 */
public class NotificationNotFoundException extends NotificationDomainException {

    public static final String CODE = "NOTIFICATION_NOT_FOUND";

    public NotificationNotFoundException(String id) {
        super("No notification with id '" + id + "' is visible to the caller");
    }
}
