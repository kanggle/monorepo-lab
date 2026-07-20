package com.example.product.infrastructure.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

/**
 * Idempotent-consumer dedupe for the reservation saga (TASK-BE-428). Runs inside the consumer's
 * transaction ({@code MANDATORY}) so the dedupe row commits atomically with the reservation /
 * stock mutation. Mirrors {@code WmsReconciliationDedupe}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReservationEventDedupe {

    private final ReservationProcessedEventRepository processedEventRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isDuplicate(UUID eventId, String eventType) {
        if (eventId == null) {
            log.warn("reservation event has null eventId — skipping dedupe. eventType={}", eventType);
            return false;
        }
        if (processedEventRepository.existsById(eventId)) {
            log.debug("Duplicate reservation event, skipping. eventId={}, eventType={}", eventId, eventType);
            return true;
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
        return false;
    }
}
