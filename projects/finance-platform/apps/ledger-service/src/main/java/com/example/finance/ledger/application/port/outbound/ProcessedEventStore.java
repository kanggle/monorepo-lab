package com.example.finance.ledger.application.port.outbound;

import java.time.Instant;

/**
 * Outbound port for the consumer dedupe store ({@code processed_events}, F1/T8).
 * A duplicate {@code eventId} is detected via {@link #isProcessed} and the event
 * skipped without mutation; {@link #markProcessed} records the id inside the same
 * transaction as the journal entry + audit row (at-most-once posting).
 */
public interface ProcessedEventStore {

    /** True iff this signed source {@code eventId} has already been applied. */
    boolean isProcessed(String eventId);

    /** Records {@code eventId} as processed (provenance: topic + source txn id). */
    void markProcessed(String eventId, String tenantId, String topic,
                       String sourceTransactionId, Instant processedAt);
}
