package com.wms.master.application.service;

import com.wms.master.application.port.out.DomainEventPort;
import com.wms.master.application.port.out.LotPersistencePort;
import com.wms.master.domain.event.LotExpiredEvent;
import com.wms.master.domain.exception.ConcurrencyConflictException;
import com.wms.master.domain.model.Lot;
import java.time.Instant;
import java.util.List;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Per-row transactional boundary for the Lot expiration batch. Extracted into
 * its own Spring-managed bean so that the {@link #expireOne(Lot, Instant)}
 * call crosses a proxy boundary — {@code @Transactional(REQUIRES_NEW)} only
 * starts a fresh transaction when the caller reaches the method through
 * Spring's AOP proxy, NOT when {@code LotService} self-calls its own
 * {@code this.expireOne(...)} (TASK-BE-018 item 2; see the task spec
 * §Implementation Notes).
 *
 * <p>Consequence: a failure mid-row (constraint violation, optimistic lock
 * contention, concurrent deactivation) rolls back only that row's transaction.
 * The surrounding batch loop in {@link LotService#execute} continues and
 * records the failure in its counters.
 *
 * <p>The domain-event publish happens inside the same per-row transaction so
 * that the outbox write and the state transition remain atomic — a crash
 * between the row update and the event write is impossible, matching the
 * {@code SkuService}/{@code WarehouseService} invariant.
 */
@Component
public class LotExpirationBatchProcessor {

    private static final String AGGREGATE_TYPE = "Lot";
    private static final String SYSTEM_ACTOR = "system";

    private final LotPersistencePort persistencePort;
    private final DomainEventPort eventPort;

    public LotExpirationBatchProcessor(LotPersistencePort persistencePort, DomainEventPort eventPort) {
        this.persistencePort = persistencePort;
        this.eventPort = eventPort;
    }

    /**
     * Transition a single lot to EXPIRED in its own new transaction. Any
     * {@link RuntimeException} thrown from this method rolls back ONLY this
     * row; the caller (batch loop) catches it and continues with the next
     * candidate.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void expireOne(Lot candidate, Instant scheduledAt) {
        candidate.expire(SYSTEM_ACTOR);
        Lot saved;
        try {
            saved = persistencePort.update(candidate);
        } catch (ObjectOptimisticLockingFailureException e) {
            throw new ConcurrencyConflictException(AGGREGATE_TYPE, candidate.getId().toString());
        }
        eventPort.publish(List.of(LotExpiredEvent.from(saved, scheduledAt)));
    }
}
