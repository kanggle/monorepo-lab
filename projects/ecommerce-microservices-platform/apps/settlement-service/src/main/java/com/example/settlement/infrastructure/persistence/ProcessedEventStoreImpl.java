package com.example.settlement.infrastructure.persistence;

import com.example.settlement.domain.repository.ProcessedEventStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dedupe over the local {@code processed_event} table. Runs inside the consumer's
 * transaction ({@code MANDATORY}) so the dedupe record and the ledger write commit
 * atomically (AC-6). A concurrent insert race resolves to "duplicate".
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcessedEventStoreImpl implements ProcessedEventStore {

    private final ProcessedEventJpaRepository repository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isDuplicate(String eventId, String eventType) {
        if (eventId == null || eventId.isBlank()) {
            log.warn("event_id null/blank — skipping dedupe (falling back to business key). eventType={}",
                    eventType);
            return false;
        }
        if (repository.existsByEventId(eventId)) {
            log.warn("Duplicate event, skipping. eventId={}, eventType={}", eventId, eventType);
            return true;
        }
        // No try/catch here, deliberately (TASK-BE-541). ProcessedEventJpaEntity uses an
        // assigned @Id, so Hibernate queues this INSERT until the commit-time flush — which
        // runs after this method returns. A catch around save() could never fire; the one
        // that used to sit here was dead code.
        //
        // The concurrent-duplicate case is still handled correctly, by retry rather than by
        // catch: the loser's flush fails, the consumer transaction rolls back, and on
        // redelivery the existsByEventId check above sees the winner's committed row and
        // returns true. Catching here could not improve on that — MANDATORY propagation
        // (required for the AC-6 atomicity above) means a flushed constraint violation
        // marks the consumer's transaction rollback-only, so "catch and carry on" is not
        // available at this layer.
        repository.save(ProcessedEventJpaEntity.create(eventId, eventType));
        return false;
    }
}
