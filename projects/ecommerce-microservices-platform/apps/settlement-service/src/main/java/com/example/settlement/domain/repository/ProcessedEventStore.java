package com.example.settlement.domain.repository;

/**
 * Dedupe port over the local {@code processed_event} table. A consumer records each
 * envelope {@code event_id} in the same transaction as the ledger write, so a
 * re-delivered event is a no-op (at-most-once accrual under at-least-once delivery,
 * AC-6).
 */
public interface ProcessedEventStore {

    /**
     * Records {@code eventId} as processed and returns {@code true} if it was
     * already present (a duplicate — caller should skip). A blank id is never a
     * duplicate (caller falls back to its business key).
     */
    boolean isDuplicate(String eventId, String eventType);
}
