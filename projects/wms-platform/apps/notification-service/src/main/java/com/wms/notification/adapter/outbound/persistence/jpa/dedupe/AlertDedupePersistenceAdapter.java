package com.wms.notification.adapter.outbound.persistence.jpa.dedupe;

import com.wms.notification.application.port.outbound.AlertDedupePort;
import com.wms.notification.domain.delivery.DedupeOutcome;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.time.Clock;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Insert-or-skip pattern for {@code notification_event_dedupe}. Mirrors the
 * inventory-service {@code EventDedupePersistenceAdapter} learning — flush
 * inside the try so the duplicate signal arrives at the catch site, not at
 * commit time.
 */
@Component
public class AlertDedupePersistenceAdapter implements AlertDedupePort {

    private final NotificationEventDedupeJpaRepository repository;
    private final Clock clock;

    @PersistenceContext
    private EntityManager entityManager;

    public AlertDedupePersistenceAdapter(NotificationEventDedupeJpaRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Result recordIfAbsent(UUID eventId, String sourceTopic, DedupeOutcome outcome) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null");
        }
        try {
            NotificationEventDedupeJpaEntity row = new NotificationEventDedupeJpaEntity(
                    eventId, sourceTopic, clock.instant(), outcome.name());
            repository.save(row);
            entityManager.flush();
            return Result.INSERTED;
        } catch (DataIntegrityViolationException duplicate) {
            return Result.DUPLICATE;
        }
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public boolean exists(UUID eventId) {
        return eventId != null && repository.existsById(eventId);
    }
}
