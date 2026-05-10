package com.wms.outbound.application.saga;

import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.SagaStatus;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Outbound saga sweeper (TASK-BE-050).
 *
 * <p>Runs every minute (configurable via
 * {@code outbound.saga.sweeper.fixed-delay-ms}). For each non-terminal
 * sweepable state, queries the saga store for rows whose
 * {@code last_transition_at} is older than the configured grace period
 * and dispatches per-saga recovery to {@link SagaRecoveryHandler}.
 *
 * <p><b>Cross-bean delegation</b>: per-saga work runs in a separate
 * {@link SagaRecoveryHandler} bean so the {@code REQUIRES_NEW}
 * {@code @Transactional} call goes through the Spring AOP proxy. Calling
 * a private {@code @Transactional} method on {@code this} would silently
 * fall back to the calling thread's TX — the regression captured in
 * memory {@code feedback_refactor_code_baseline_it.md}.
 *
 * <p><b>DB-clock vs JVM-clock</b>:
 * {@link SagaPersistencePort#findStuck} uses {@code now()} on the database
 * to compute staleness — this avoids skew between sweeper replicas. The
 * sweeper itself never reads {@code Instant.now()} for the predicate.
 *
 * <p><b>Concurrency</b>: single instance per service replica. Multiple
 * replicas racing on the same saga are absorbed by:
 * <ol>
 *   <li>The saga's optimistic-lock {@code version} (T5) — second writer
 *       raises {@code OptimisticLockingFailureException}.</li>
 *   <li>Inventory consumer-side eventId dedupe + the saga state-machine
 *       guard — re-emissions are idempotent end-to-end.</li>
 * </ol>
 *
 * <p>Disabled by default in {@code standalone} profile (sweeper has no
 * Kafka / DB dependency to recover into). Disabled in {@code test}
 * profile so unit/integration tests can drive {@link #sweep()} directly.
 */
@Component
@Profile("!test")
@ConditionalOnProperty(name = "outbound.saga.sweeper.enabled",
        havingValue = "true", matchIfMissing = true)
public class SagaSweeper {

    private static final Logger log = LoggerFactory.getLogger(SagaSweeper.class);

    private static final String EVENT_PICKING_REQUESTED = "outbound.picking.requested";
    private static final String EVENT_PICKING_CANCELLED = "outbound.picking.cancelled";
    private static final String EVENT_SHIPPING_CONFIRMED = "outbound.shipping.confirmed";

    private static final String METRIC_RUN = "outbound.saga.sweeper.run.count";

    private final SagaPersistencePort sagaPersistence;
    private final SagaRecoveryHandler recoveryHandler;
    private final MeterRegistry meterRegistry;

    private final long graceSeconds;
    private final int batchSize;
    private final int maxAttempts;

    public SagaSweeper(SagaPersistencePort sagaPersistence,
                       SagaRecoveryHandler recoveryHandler,
                       MeterRegistry meterRegistry,
                       @Value("${outbound.saga.sweeper.threshold-seconds:300}") long graceSeconds,
                       @Value("${outbound.saga.sweeper.batch-size:100}") int batchSize,
                       @Value("${outbound.saga.sweeper.max-attempts:5}") int maxAttempts) {
        this.sagaPersistence = sagaPersistence;
        this.recoveryHandler = recoveryHandler;
        this.meterRegistry = meterRegistry;
        this.graceSeconds = graceSeconds;
        this.batchSize = batchSize;
        this.maxAttempts = maxAttempts;
    }

    /**
     * Scheduled tick: 1-minute fixed-delay (configurable). Per-tick the
     * sweeper checks all three sweepable states. Exceptions inside the
     * loop are caught here so the scheduler thread never dies — the next
     * tick re-scans.
     */
    @Scheduled(fixedDelayString = "${outbound.saga.sweeper.fixed-delay-ms:60000}",
            initialDelayString = "${outbound.saga.sweeper.initial-delay-ms:30000}")
    public void sweep() {
        meterRegistry.counter(METRIC_RUN).increment();
        try {
            sweepState(SagaStatus.REQUESTED, EVENT_PICKING_REQUESTED);
            sweepState(SagaStatus.CANCELLATION_REQUESTED, EVENT_PICKING_CANCELLED);
            sweepState(SagaStatus.SHIPPED, EVENT_SHIPPING_CONFIRMED);
        } catch (Exception e) {
            // Defence-in-depth — sweepState should already swallow per-saga
            // failures, but a query-level failure must not kill the
            // scheduler.
            log.error("saga_sweeper_tick_failed reason={}", e.toString(), e);
        }
    }

    /**
     * Visible for tests / the IT harness — drives a single tick on demand.
     */
    public void sweepOnce() {
        sweep();
    }

    /**
     * Visible for tests so the IT harness can read the configured cap
     * without re-parsing properties.
     */
    public int maxAttempts() {
        return maxAttempts;
    }

    /**
     * Visible for tests so the IT harness can read the configured cap
     * without re-parsing properties.
     */
    public long graceSeconds() {
        return graceSeconds;
    }

    private void sweepState(SagaStatus state, String eventType) {
        Duration grace = Duration.ofSeconds(graceSeconds);
        List<OutboundSaga> stuck;
        try {
            stuck = sagaPersistence.findStuck(state, grace, batchSize);
        } catch (Exception e) {
            log.error("saga_sweeper_findStuck_failed state={} reason={}",
                    state, e.toString(), e);
            return;
        }
        if (stuck.isEmpty()) {
            return;
        }
        log.info("saga_sweeper_batch state={} count={}", state, stuck.size());
        for (OutboundSaga saga : stuck) {
            try {
                recoveryHandler.recover(saga.sagaId(), eventType, maxAttempts);
            } catch (Exception e) {
                // Per-saga failure isolated. Next tick retries.
                log.warn("saga_sweeper_saga_failed sagaId={} reason={}",
                        saga.sagaId(), e.toString());
            }
        }
    }
}
