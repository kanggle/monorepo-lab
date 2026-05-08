package com.wms.admin.infra.persistence.readmodel;

import com.wms.admin.application.port.AdminEventDedupePort;
import com.wms.admin.application.projection.DedupeOutcome;
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
 * JPA-backed implementation of {@link AdminEventDedupePort}.
 *
 * <p>Insert-then-flush so the duplicate signal arrives at the catch site, not
 * at TX commit (the same pattern inventory-service /
 * notification-service already use — TASK-MONO-046 / BE-043 learning).
 * Runs inside the caller's outer projection transaction
 * ({@link Propagation#MANDATORY}) so the dedupe row, the read-model mutation,
 * and any append rows commit or roll back together.
 */
@Component
public class AdminEventDedupePersistenceAdapter implements AdminEventDedupePort {

    private static final Logger log =
            LoggerFactory.getLogger(AdminEventDedupePersistenceAdapter.class);

    private final AdminEventDedupeJpaRepository repository;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public AdminEventDedupePersistenceAdapter(AdminEventDedupeJpaRepository repository,
                                              Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public DedupeOutcome tryRecord(UUID eventId, String eventType) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null");
        }
        try {
            AdminEventDedupeJpaEntity row = new AdminEventDedupeJpaEntity(
                    eventId, eventType, clock.instant(), DedupeOutcome.APPLIED.name());
            repository.save(row);
            entityManager.flush();
            return DedupeOutcome.APPLIED;
        } catch (DataIntegrityViolationException duplicate) {
            log.debug("event {} ({}) already processed; skipping", eventId, eventType);
            return DedupeOutcome.DUPLICATE;
        }
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void markStale(UUID eventId) {
        repository.findById(eventId).ifPresent(row -> {
            row.setOutcome(DedupeOutcome.IGNORED_DUPLICATE_LATE.name());
            repository.save(row);
        });
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public LifetimeCounts countLifetime() {
        return new LifetimeCounts(
                repository.countByOutcome(DedupeOutcome.APPLIED.name()),
                repository.countByOutcome(DedupeOutcome.DUPLICATE.name()),
                repository.countByOutcome(DedupeOutcome.IGNORED_DUPLICATE_LATE.name()),
                repository.countByOutcome(DedupeOutcome.FAILED.name()));
    }
}
