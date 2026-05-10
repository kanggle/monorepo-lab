package com.wms.outbound.application.saga;

import com.wms.outbound.application.port.out.OutboxReEmitterPort;
import com.wms.outbound.application.port.out.OutboxWriterPort;
import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.domain.event.SagaRecoveryExhaustedEvent;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.SagaStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Saga sweeper per-saga handler (TASK-BE-050).
 *
 * <p>Lives on a separate bean from {@link SagaSweeper} so the
 * {@code REQUIRES_NEW} {@code @Transactional} call is intercepted by the
 * Spring AOP proxy. Self-invocation from {@code SagaSweeper} would bypass
 * the proxy and silently downgrade to the calling thread's TX (the
 * regression captured in memory {@code feedback_refactor_code_baseline_it.md}
 * — KafkaListener / @Transactional self-invocation lessons).
 *
 * <p>Each call runs in its **own** transaction:
 * <ul>
 *   <li>If {@code re_emit_count + 1 < maxAttempts}: re-emit the appropriate
 *       outbox event (clone via {@link OutboxReEmitterPort}), bump
 *       {@code re_emit_count}, save saga, increment recovery-fired metric.</li>
 *   <li>If {@code re_emit_count + 1 >= maxAttempts}: transition saga to
 *       {@link SagaStatus#STUCK_RECOVERY_FAILED}, emit
 *       {@code outbound.alert.saga.recovery.exhausted} via the standard
 *       outbox writer, increment exhausted metric. The two writes commit
 *       atomically (T3).</li>
 * </ul>
 *
 * <p>Exceptions inside this method roll back its own TX only — the
 * {@link SagaSweeper} loop keeps iterating across other stuck sagas.
 * {@code OptimisticLockingFailureException} is re-thrown so the sweeper
 * can log it; the next tick retries with a fresh load.
 */
@Component
public class SagaRecoveryHandler {

    private static final Logger log = LoggerFactory.getLogger(SagaRecoveryHandler.class);

    private static final String FAILURE_REASON = "saga_recovery_attempts_exhausted";
    private static final String METRIC_RECOVERY_FIRED = "outbound.saga.sweeper.recovery.fired";
    private static final String METRIC_EXHAUSTED = "outbound.saga.sweeper.exhausted.count";

    private final SagaPersistencePort sagaPersistence;
    private final OutboxReEmitterPort outboxReEmitter;
    private final OutboxWriterPort outboxWriter;
    private final MeterRegistry meterRegistry;
    private final Clock clock;

    public SagaRecoveryHandler(SagaPersistencePort sagaPersistence,
                               OutboxReEmitterPort outboxReEmitter,
                               OutboxWriterPort outboxWriter,
                               MeterRegistry meterRegistry,
                               Clock clock) {
        this.sagaPersistence = sagaPersistence;
        this.outboxReEmitter = outboxReEmitter;
        this.outboxWriter = outboxWriter;
        this.meterRegistry = meterRegistry;
        this.clock = clock;
    }

    /**
     * Apply one recovery attempt to a single saga. Runs in
     * {@link Propagation#REQUIRES_NEW} so a failure on one saga does not
     * roll back others in the same sweeper tick.
     *
     * @param sagaId saga to recover
     * @param eventType outbox event type to re-emit (one of:
     *                  {@code outbound.picking.requested},
     *                  {@code outbound.picking.cancelled},
     *                  {@code outbound.shipping.confirmed})
     * @param maxAttempts cap above which the saga is marked
     *                    {@link SagaStatus#STUCK_RECOVERY_FAILED}
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recover(java.util.UUID sagaId, String eventType, int maxAttempts) {
        OutboundSaga saga = sagaPersistence.findById(sagaId).orElse(null);
        if (saga == null) {
            log.warn("saga_sweeper_saga_vanished sagaId={}", sagaId);
            return;
        }
        // Re-check state + count under fresh load — saga may have advanced
        // between sweeper's findStuck() and now (consumer race).
        if (!isSweepableState(saga.status())) {
            log.debug("saga_sweeper_skip_advanced sagaId={} state={}",
                    sagaId, saga.status());
            return;
        }
        int nextAttempt = saga.reEmitCount() + 1;
        Instant now = clock.instant();
        SagaStatus stuckState = saga.status();

        if (nextAttempt >= maxAttempts) {
            markExhausted(saga, stuckState, nextAttempt, now);
            return;
        }

        boolean reEmitted = outboxReEmitter.reEmit(saga.sagaId(), eventType);
        if (!reEmitted) {
            // Defensive: missing original outbox row. Bump counter anyway so
            // the saga eventually reaches STUCK_RECOVERY_FAILED and an
            // operator is alerted.
            log.warn("saga_sweeper_reemit_skipped_no_original sagaId={} eventType={}",
                    sagaId, eventType);
        }
        saga.recordReEmission(now);
        sagaPersistence.save(saga);

        meterRegistry.counter(METRIC_RECOVERY_FIRED, "from_state", stuckState.name())
                .increment();
        log.info("saga_sweeper_reemission sagaId={} state={} attempt={} eventType={}",
                sagaId, stuckState, nextAttempt, eventType);
    }

    private void markExhausted(OutboundSaga saga, SagaStatus stuckState,
                               int finalAttemptCount, Instant now) {
        // Persist the final attempt count so the alert event carries the
        // correct number, then transition to terminal STUCK_RECOVERY_FAILED.
        saga.recordReEmission(now);
        Instant lastTransition = saga.lastTransitionAt();
        saga.markStuckRecoveryFailed(FAILURE_REASON, now);
        sagaPersistence.save(saga);

        SagaRecoveryExhaustedEvent alert = new SagaRecoveryExhaustedEvent(
                saga.sagaId(),
                saga.orderId(),
                stuckState.name(),
                finalAttemptCount,
                lastTransition,
                FAILURE_REASON,
                now,
                now,
                "system:saga-sweeper");
        outboxWriter.publish(alert);

        meterRegistry.counter(METRIC_EXHAUSTED, "from_state", stuckState.name())
                .increment();
        log.warn("saga_stuck sagaId={} state={} attempts={}",
                saga.sagaId(), stuckState, finalAttemptCount);
    }

    private static boolean isSweepableState(SagaStatus s) {
        return s == SagaStatus.REQUESTED
                || s == SagaStatus.CANCELLATION_REQUESTED
                || s == SagaStatus.SHIPPED;
    }
}
