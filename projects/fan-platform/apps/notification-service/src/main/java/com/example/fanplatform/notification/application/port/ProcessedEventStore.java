package com.example.fanplatform.notification.application.port;

/**
 * Consumer idempotency port (architecture.md § Idempotency). The use case checks
 * {@link #alreadyProcessed(String)} before handling and records
 * {@link #markProcessed(String, String)} after a successful handle, both inside
 * the same transaction so the {@code processed_events} insert commits atomically
 * with the {@code Notification} row.
 */
public interface ProcessedEventStore {

    boolean alreadyProcessed(String eventId);

    void markProcessed(String eventId, String eventType);
}
