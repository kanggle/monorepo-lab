package com.example.erp.notification.domain.error;

/**
 * masterdata could not be asked which employee the caller is (TASK-MONO-776). Maps to 503
 * {@code SERVICE_UNAVAILABLE} (platform-common). Deliberately NOT an empty inbox: «your inbox is
 * empty» during a masterdata outage would be a false statement about the caller's data.
 */
public class RecipientResolveUnavailableException extends NotificationDomainException {

    public static final String CODE = "SERVICE_UNAVAILABLE";

    public RecipientResolveUnavailableException() {
        super("Could not resolve the calling account's employee (masterdata unavailable)");
    }
}
