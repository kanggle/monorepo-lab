package com.wms.notification.adapter.outbound.persistence.jpa.dedupe;

import com.wms.notification.application.port.out.AlertDedupePort;
import com.wms.notification.domain.delivery.DedupeOutcome;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Insert-or-skip dedupe for {@code notification_event_dedupe} via
 * {@code INSERT … ON CONFLICT (event_id) DO NOTHING}. The affected-row count is
 * the signal: 1 means this eventId is new ({@link Result#INSERTED}), 0 means it
 * was already recorded ({@link Result#DUPLICATE}).
 *
 * <p>Why not {@code repository.save(...)}: the dedupe entity's {@code @Id} is a
 * caller-assigned non-null UUID with no {@code @Version}, so Spring Data
 * {@code save()} routes to {@code merge()} — a silent SELECT-then-UPDATE upsert
 * that never collides on a duplicate PK, so a redelivered event would be
 * re-processed (TASK-BE-488). The unconditional {@code ON CONFLICT DO NOTHING}
 * insert never throws, so it cannot poison the caller's {@code MANDATORY}
 * transaction with rollback-only state.
 */
@Component
public class AlertDedupeRepositoryImpl implements AlertDedupePort {

    private final NotificationEventDedupeJpaRepository repository;
    private final Clock clock;

    public AlertDedupeRepositoryImpl(NotificationEventDedupeJpaRepository repository,
                                     Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Result recordIfAbsent(UUID eventId, String sourceTopic, DedupeOutcome outcome) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null");
        }
        int inserted = repository.insertIfAbsent(
                eventId, sourceTopic, clock.instant(), outcome.name());
        return inserted == 0 ? Result.DUPLICATE : Result.INSERTED;
    }

    @Override
    @Transactional(readOnly = true, propagation = Propagation.SUPPORTS)
    public boolean exists(UUID eventId) {
        return eventId != null && repository.existsById(eventId);
    }
}
