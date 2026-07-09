package com.wms.outbound.adapter.out.persistence.adapter;

import com.wms.outbound.adapter.out.persistence.repository.OutboundEventDedupeRepository;
import com.wms.outbound.application.port.out.EventDedupePort;
import java.time.Clock;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence adapter for {@link EventDedupePort}.
 *
 * <p>Implementation: {@code INSERT … ON CONFLICT (event_id) DO NOTHING} via
 * {@link OutboundEventDedupeRepository#insertIfAbsent}. The affected-row count is
 * the dedupe signal — 1 means this eventId is new (run the work), 0 means it was
 * already processed (skip and return {@link Outcome#IGNORED_DUPLICATE}).
 *
 * <p>The insert runs inside the caller's outer transaction
 * ({@link Propagation#MANDATORY}) so the dedupe row, the consumer's domain
 * writes, and any outbox writes commit or rollback together.
 *
 * <p>Why not {@code repository.save(...)}: the dedupe entity's {@code @Id} is a
 * caller-assigned non-null UUID with no {@code @Version}, so Spring Data
 * {@code save()} routes to {@code merge()} — a silent SELECT-then-UPDATE upsert
 * that never collides on a duplicate PK. That made every redelivered event
 * re-apply its side effects (TASK-BE-488). The unconditional {@code ON CONFLICT
 * DO NOTHING} insert is the fix and never throws, so it cannot poison this
 * MANDATORY transaction with rollback-only state.
 */
@Component
public class EventDedupeRepositoryImpl implements EventDedupePort {

    private static final Logger log = LoggerFactory.getLogger(EventDedupeRepositoryImpl.class);

    private final OutboundEventDedupeRepository repository;
    private final Clock clock;

    public EventDedupeRepositoryImpl(OutboundEventDedupeRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome process(UUID eventId, String eventType, Runnable work) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null");
        }
        int inserted = repository.insertIfAbsent(
                eventId, eventType, clock.instant(), Outcome.APPLIED.name());
        if (inserted == 0) {
            log.debug("event {} ({}) already processed; skipping", eventId, eventType);
            return Outcome.IGNORED_DUPLICATE;
        }
        work.run();
        return Outcome.APPLIED;
    }
}
