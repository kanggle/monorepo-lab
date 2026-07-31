package com.example.fanplatform.notification.infrastructure.messaging.idempotency;

import com.example.fanplatform.notification.domain.time.ClockPort;
import com.example.messaging.dedupe.EventDedupePort;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * JPA-backed {@link EventDedupePort} adoption (ADR-MONO-058 § D7, TASK-FAN-BE-042)
 * replacing the check-then-act {@code ProcessedEventStore}/{@code JpaProcessedEventStore}
 * pair.
 *
 * <p>Implementation: {@code INSERT … ON CONFLICT (event_id) DO NOTHING} via
 * {@link ProcessedEventJpaRepository#insertIfAbsent} — the affected-row count is
 * the dedupe signal (1 = first sighting, run {@code work}; 0 = duplicate, skip).
 * This is <b>PK-violation-based</b>, not existence-pre-check-based: a naive
 * "{@code existsByEventId} then {@code save}" re-implementation would reintroduce
 * the TOCTOU window under concurrent delivery of the same {@code eventId} that
 * this adoption exists to close (task Failure Scenarios).
 *
 * <p>{@code repository.save(...)} is deliberately avoided: for an entity whose
 * {@code @Id} is caller-assigned with no {@code @Version}, Spring Data routes
 * {@code save()} to {@code EntityManager.merge()} — a silent SELECT-then-UPDATE
 * upsert that never collides on a duplicate PK (the class of bug wms's
 * TASK-BE-488 hit against the equivalent adapter shape). The unconditional
 * {@code ON CONFLICT DO NOTHING} insert never throws, so it cannot poison this
 * {@link Propagation#MANDATORY} transaction with rollback-only state before
 * {@code work} even runs.
 *
 * <p>{@link Propagation#MANDATORY}: the insert runs inside the caller's
 * {@code @Transactional} use-case method (the caller is the ONLY transaction
 * boundary — this adapter never opens its own), so the dedupe row commits or
 * rolls back atomically with the {@code Notification} row (rules/traits/
 * transactional.md §T8). If {@code work} throws, the exception propagates
 * unchanged and Spring rolls the whole transaction back — including the
 * already-inserted dedupe row.
 */
@Component
@RequiredArgsConstructor
public class EventDedupePortJpaAdapter implements EventDedupePort {

    private static final Logger log = LoggerFactory.getLogger(EventDedupePortJpaAdapter.class);

    private final ProcessedEventJpaRepository repository;
    private final ClockPort clock;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Outcome process(UUID eventId, String eventType, Runnable work) {
        if (eventId == null) {
            throw new IllegalArgumentException("eventId must not be null");
        }
        int inserted = repository.insertIfAbsent(eventId.toString(), eventType, clock.now());
        if (inserted == 0) {
            log.debug("event {} ({}) already processed; skipping", eventId, eventType);
            return Outcome.IGNORED_DUPLICATE;
        }
        work.run();
        return Outcome.APPLIED;
    }
}
