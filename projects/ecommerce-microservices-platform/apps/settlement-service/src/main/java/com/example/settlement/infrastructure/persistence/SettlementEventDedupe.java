package com.example.settlement.infrastructure.persistence;

import com.example.messaging.dedupe.EventDedupePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * {@link EventDedupePort} adapter over settlement-service's locally-owned
 * {@code processed_event} table (ADR-MONO-058 D7, TASK-BE-569).
 *
 * <p>Replaces the service-local {@code ProcessedEventStore} domain port +
 * {@code ProcessedEventStoreImpl} — that abstraction existed only to wrap this exact
 * table with a single-method interface; now that consumers call the shared port
 * directly, keeping the local port would have left two competing dedupe abstractions
 * doing the same job (the failure scenario this task's own scope explicitly warns
 * against). All 3 settlement consumers (order-placed snapshot, payment-completed
 * accrual, payment-refunded reversal) call this bean directly.
 *
 * <p>The table's {@code @Id} is a {@code String} (pre-dates the shared port), so this
 * adapter stores {@code eventId.toString()} — a storage-format detail only, the dedupe
 * semantics (same eventId twice → the second is IGNORED_DUPLICATE) are unchanged. Runs
 * inside the consumer's transaction ({@code MANDATORY}) so the dedupe row commits
 * atomically with the ledger write (AC-6).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementEventDedupe implements EventDedupePort {

    private final ProcessedEventJpaRepository repository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome process(UUID eventId, String eventType, Runnable work) {
        if (eventId == null) {
            log.warn("event_id null — skipping dedupe (falling back to business key). eventType={}", eventType);
            work.run();
            return Outcome.APPLIED;
        }

        String key = eventId.toString();
        if (repository.existsByEventId(key)) {
            log.warn("Duplicate event, skipping. eventId={}, eventType={}", key, eventType);
            return Outcome.IGNORED_DUPLICATE;
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
        repository.save(ProcessedEventJpaEntity.create(key, eventType));
        work.run();
        return Outcome.APPLIED;
    }
}
