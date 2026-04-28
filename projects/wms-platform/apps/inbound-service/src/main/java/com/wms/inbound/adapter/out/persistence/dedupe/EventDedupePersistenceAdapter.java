package com.wms.inbound.adapter.out.persistence.dedupe;

import com.wms.inbound.application.port.out.EventDedupePort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence adapter for {@link EventDedupePort}.
 *
 * <p>Implementation: insert-then-flush. A unique-PK violation on
 * {@link DataIntegrityViolationException} signals a duplicate event — the
 * supplied work is skipped and {@link Outcome#IGNORED_DUPLICATE} is returned.
 *
 * <p>The dedupe insert runs inside the caller's outer transaction
 * ({@link Propagation#MANDATORY}) so the dedupe row, the consumer's domain
 * writes, and any outbox writes commit or rollback together. If {@code work}
 * throws, the transaction is rolled back and the dedupe row is removed
 * alongside, allowing a redelivery to retry. Mirrors
 * inventory-service's TASK-BE-027 pattern.
 */
@Component
public class EventDedupePersistenceAdapter implements EventDedupePort {

    private static final Logger log = LoggerFactory.getLogger(EventDedupePersistenceAdapter.class);

    private final EventDedupeJpaRepository repository;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public EventDedupePersistenceAdapter(EventDedupeJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome process(UUID eventId, String eventType, Runnable work) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null");
        }
        try {
            EventDedupeJpaEntity row = new EventDedupeJpaEntity(
                    eventId, eventType, clock.instant(), Outcome.APPLIED.name());
            repository.save(row);
            // Force the constraint check before running the side-effect so the
            // duplicate signal arrives at the catch site, not at TX commit.
            entityManager.flush();
        } catch (DataIntegrityViolationException duplicate) {
            log.debug("event {} ({}) already processed; skipping", eventId, eventType);
            return Outcome.IGNORED_DUPLICATE;
        }
        work.run();
        return Outcome.APPLIED;
    }
}
