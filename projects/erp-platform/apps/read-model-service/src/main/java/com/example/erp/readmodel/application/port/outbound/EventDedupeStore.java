package com.example.erp.readmodel.application.port.outbound;

import com.example.erp.readmodel.domain.dedupe.EventDedupeRecord;

import java.util.Optional;

/**
 * Outbound port for the consumer dedupe store ({@code processed_events}, T8).
 * A duplicate {@code eventId} is detected via {@link #isProcessed} and the
 * event skipped without mutation; {@link #markProcessed} records processing
 * provenance inside the same transaction as the projection upsert.
 */
public interface EventDedupeStore {

    /** True iff this {@code eventId} has already been applied. */
    boolean isProcessed(String eventId);

    Optional<EventDedupeRecord> find(String eventId);

    /** Records {@code eventId} as processed (provenance: topic + aggregateId). */
    void markProcessed(EventDedupeRecord record);
}
