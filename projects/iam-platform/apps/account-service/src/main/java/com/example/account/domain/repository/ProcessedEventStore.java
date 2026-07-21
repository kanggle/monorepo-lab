package com.example.account.domain.repository;

/**
 * Port for the consumer-side idempotency dedup store keyed on {@code eventId} (the
 * {@code processed_events} table). Implemented in the infrastructure layer so the consumer
 * application path never depends on the JPA entity/repository directly.
 */
public interface ProcessedEventStore {

    /** @return {@code true} if an event with this id has already been recorded as processed. */
    boolean existsByEventId(String eventId);

    /**
     * Records {@code eventId} (of {@code eventType}) as processed, flushing the insert
     * immediately. The eager flush is a behavioural contract: a concurrent redelivery that
     * lost the dedup-row race fails fast <em>here</em> (a data-integrity / unique-key
     * violation) instead of at deferred commit-time flush, so the caller can detect and skip
     * it inside its own try/catch.
     */
    void markProcessed(String eventId, String eventType);
}
