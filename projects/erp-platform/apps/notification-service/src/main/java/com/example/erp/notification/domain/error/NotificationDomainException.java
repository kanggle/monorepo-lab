package com.example.erp.notification.domain.error;

/** Base for notification-service domain exceptions (pure domain — no framework). */
public abstract class NotificationDomainException extends RuntimeException {

    protected NotificationDomainException(String message) {
        super(message);
    }
}
