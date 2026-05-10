package com.example.messaging.outbox;

import java.util.List;
import java.util.UUID;

/**
 * Repository contract for the {@link AbstractOutboxPublisher} loop.
 *
 * <p>Implementations are typically Spring Data JPA repositories on a per-service
 * outbox entity, but the publisher only needs a narrow surface so any backend is
 * acceptable.
 */
public interface OutboxRowRepository<R extends OutboxRow> {

    /**
     * Fetch the next batch of pending rows (where {@code publishedAt IS NULL}),
     * ordered ascending by their natural created-at order so older rows publish
     * first.
     *
     * @param batchSize maximum number of rows
     */
    List<R> findPending(int batchSize);

    /**
     * Re-load the row by id within the calling transaction so the publisher can
     * mark it published in a fresh transaction. Returns {@code null} if the row
     * has been deleted by another path (in which case the publisher logs and
     * skips the persist).
     */
    R findById(UUID id);

    /**
     * Persist the row. The publisher calls this after invoking {@link OutboxRow#markPublished}
     * inside a fresh transaction.
     */
    void save(R row);

    /**
     * Count of unpublished rows. Powers the {@code *.outbox.pending.count} gauge.
     */
    long countPending();
}
