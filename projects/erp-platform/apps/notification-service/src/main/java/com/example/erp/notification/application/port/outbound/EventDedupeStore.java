package com.example.erp.notification.application.port.outbound;

import com.example.erp.notification.domain.dedupe.EventDedupeRecord;

/**
 * Outbound port for the consumer dedupe store ({@code processed_events}, T8). A
 * duplicate {@code eventId} is detected via {@link #isProcessed} and the event
 * skipped without mutation; {@link #markProcessed} records dispatch provenance
 * inside the same transaction as the notification + delivery write.
 */
public interface EventDedupeStore {

    /** True iff this {@code eventId} has already been dispatched. */
    boolean isProcessed(String eventId);

    /** Records {@code eventId} as processed (provenance: topic + aggregateId). */
    void markProcessed(EventDedupeRecord record);
}
