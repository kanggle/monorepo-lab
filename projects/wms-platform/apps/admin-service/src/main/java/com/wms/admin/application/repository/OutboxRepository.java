package com.wms.admin.application.repository;

/**
 * Append-an-event-to-outbox repository. Caller's transaction (T3 — atomic with
 * the aggregate row).
 *
 * <p>{@code payload} is the fully-serialised JSON envelope (per
 * {@code admin-events.md § Global Envelope}); the implementation only persists
 * it.
 *
 * <p>{@code partitionKey} is the aggregate id (or setting key) — see
 * {@code admin-events.md § Topic Layout}.
 */
public interface OutboxRepository {

    void append(String aggregateType,
                String aggregateId,
                String eventType,
                String payload,
                String partitionKey);
}
