package com.kanggle.platformconsole.bff.application.usecase;

/**
 * Thrown when a mark-read request names a {@code sourceDomain} that is not a
 * configured notification inbox domain (ADR-MONO-043 P3a).
 *
 * <p>Mapped by {@code GlobalExceptionHandler} to {@code 404 NOTIFICATION_NOT_FOUND}
 * (contract § 2.3 — an unknown owner is treated as an unknown notification, no
 * existence leak).
 */
public class UnknownNotificationDomainException extends RuntimeException {

    public UnknownNotificationDomainException(String sourceDomain) {
        super("Unknown notification source domain: " + sourceDomain);
    }
}
