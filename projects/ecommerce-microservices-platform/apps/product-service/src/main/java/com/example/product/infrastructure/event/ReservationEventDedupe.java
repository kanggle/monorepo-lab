package com.example.product.infrastructure.event;

import com.example.messaging.dedupe.EventDedupePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * {@link EventDedupePort} adapter for the reservation saga (TASK-BE-428, ADR-MONO-058 D7).
 * Runs inside the consumer's transaction ({@code MANDATORY}) so the dedupe row commits
 * atomically with the reservation / stock mutation. Mirrors {@code WmsReconciliationDedupe} —
 * both implement the shared port but persist into separate service-owned tables, so both are
 * registered as beans; consumers disambiguate by constructor-parameter name matching this
 * bean's default name ({@code reservationEventDedupe}).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationEventDedupe implements EventDedupePort {

    private final ReservationProcessedEventJpaRepository processedEventRepository;
    private final Clock clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome process(UUID eventId, String eventType, Runnable work) {
        if (eventId == null) {
            log.warn("reservation event has null eventId — skipping dedupe. eventType={}", eventType);
            work.run();
            return Outcome.APPLIED;
        }
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Duplicate reservation event, skipping. eventId={}, eventType={}", eventId, eventType);
            return Outcome.IGNORED_DUPLICATE;
        }
        // No try/catch here, deliberately (TASK-BE-541). ReservationProcessedEventEntity
        // uses an assigned @Id (the event UUID), so Hibernate queues this INSERT until the
        // commit-time flush — which runs after this method returns. A catch around save()
        // could never fire; the one that used to sit here was dead code.
        //
        // The concurrent-duplicate case is still handled correctly, by retry rather than by
        // catch: the loser's flush fails, the consumer transaction rolls back, and on
        // redelivery the existsById check above sees the winner's committed row and returns
        // true. Catching here could not improve on that — MANDATORY propagation (required
        // for the atomicity described above) means a flushed constraint violation marks the
        // consumer's transaction rollback-only, so "catch and carry on" is not available.
        processedEventRepository.save(
                ReservationProcessedEventEntity.of(eventId, eventType, Instant.now(clock)));
        work.run();
        return Outcome.APPLIED;
    }
}
