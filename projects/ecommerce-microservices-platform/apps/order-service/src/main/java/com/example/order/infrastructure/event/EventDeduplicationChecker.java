package com.example.order.infrastructure.event;

import com.example.messaging.dedupe.EventDedupePort;
import com.example.order.infrastructure.persistence.ProcessedEventJpaEntity;
import com.example.order.infrastructure.persistence.ProcessedEventJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * {@link EventDedupePort} adapter over order-service's locally-owned {@code processed_events}
 * table (ADR-MONO-058 D7, TASK-BE-569). The table's {@code @Id} is a {@code String}
 * (pre-dates the shared port), so this adapter stores {@code eventId.toString()} —
 * a storage-format detail only, the dedupe semantics (same eventId twice → the second is
 * IGNORED_DUPLICATE) are unchanged.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventDeduplicationChecker implements EventDedupePort {

    private final ProcessedEventJpaRepository processedEventJpaRepository;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome process(UUID eventId, String eventType, Runnable work) {
        if (eventId == null) {
            log.warn("event_id is null, skipping deduplication check. eventType={}", eventType);
            work.run();
            return Outcome.APPLIED;
        }

        String key = eventId.toString();
        if (processedEventJpaRepository.existsByEventId(key)) {
            log.warn("Duplicate event detected, skipping. eventId={}, eventType={}", key, eventType);
            return Outcome.IGNORED_DUPLICATE;
        }

        // No try/catch here, deliberately (TASK-BE-541). ProcessedEventJpaEntity uses an
        // assigned @Id, so Hibernate queues this INSERT until the commit-time flush — which
        // runs after this method returns. A catch around save() could never fire; the one
        // that used to sit here was dead code, and its unit test only passed because it
        // stubbed save() to throw, which the real repository never does.
        //
        // The concurrent-duplicate case is still handled correctly, by retry rather than by
        // catch: the loser's flush fails, the consumer transaction rolls back, and on
        // redelivery the existsByEventId check above sees the winner's committed row and
        // returns true. Catching here could not improve on that — MANDATORY propagation
        // means we are inside the consumer's transaction, and a flushed constraint violation
        // marks it rollback-only, so "catch and carry on" is not available at this layer.
        processedEventJpaRepository.save(ProcessedEventJpaEntity.create(key, eventType));
        work.run();
        return Outcome.APPLIED;
    }
}
