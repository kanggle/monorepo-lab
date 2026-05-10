package com.wms.outbound.application.saga;

import static org.assertj.core.api.Assertions.assertThat;

import com.wms.outbound.application.port.out.OutboxReEmitterPort;
import com.wms.outbound.application.service.fakes.FakeOutboxWriterPort;
import com.wms.outbound.application.service.fakes.FakeSagaPersistencePort;
import com.wms.outbound.domain.event.SagaRecoveryExhaustedEvent;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.SagaStatus;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link SagaSweeper} + {@link SagaRecoveryHandler} (TASK-BE-050).
 *
 * <p>Drives the sweeper through a fake saga persistence + a recording
 * {@link OutboxReEmitterPort}. The recording outbox writer fake captures
 * the exhaustion-alert event for assertion.
 *
 * <p>The full @Scheduled wiring + REQUIRES_NEW propagation is exercised
 * in the IT — these unit tests focus on the recovery / cap / metric
 * matrix.
 */
class SagaSweeperTest {

    private static final Instant T0 = Instant.parse("2026-05-10T10:00:00Z");
    private static final Instant TICK = T0.plusSeconds(600); // sweeper fires 10 min later
    private static final long GRACE_SECONDS = 300L;          // 5 min
    private static final int MAX_ATTEMPTS = 5;

    private FakeSagaPersistencePort sagaPersistence;
    private RecordingReEmitter reEmitter;
    private FakeOutboxWriterPort outboxWriter;
    private MeterRegistry meterRegistry;
    private SagaRecoveryHandler handler;
    private SagaSweeper sweeper;

    @BeforeEach
    void setUp() {
        sagaPersistence = new FakeSagaPersistencePort();
        sagaPersistence.clock = Clock.fixed(TICK, ZoneOffset.UTC);
        reEmitter = new RecordingReEmitter();
        outboxWriter = new FakeOutboxWriterPort();
        meterRegistry = new SimpleMeterRegistry();
        Clock clock = Clock.fixed(TICK, ZoneOffset.UTC);
        handler = new SagaRecoveryHandler(sagaPersistence, reEmitter, outboxWriter,
                meterRegistry, clock);
        sweeper = new SagaSweeper(sagaPersistence, handler, meterRegistry,
                GRACE_SECONDS, 100, MAX_ATTEMPTS);
    }

    @Test
    void requestedSagaPastGraceIsReEmitted() {
        OutboundSaga saga = OutboundSaga.newRequested(UUID.randomUUID(), UUID.randomUUID(), T0);
        sagaPersistence.save(saga);

        sweeper.sweep();

        assertThat(reEmitter.calls).containsExactly(
                new ReEmitCall(saga.sagaId(), "outbound.picking.requested"));
        OutboundSaga reloaded = sagaPersistence.findById(saga.sagaId()).orElseThrow();
        assertThat(reloaded.reEmitCount()).isEqualTo(1);
        assertThat(reloaded.status()).isEqualTo(SagaStatus.REQUESTED);
        assertThat(meterRegistry.counter("outbound.saga.sweeper.recovery.fired",
                "from_state", "REQUESTED").count()).isEqualTo(1.0);
        assertThat(meterRegistry.counter("outbound.saga.sweeper.run.count").count())
                .isEqualTo(1.0);
    }

    @Test
    void cancellationRequestedPastGraceReEmitsCancelled() {
        UUID orderId = UUID.randomUUID();
        UUID sagaId = UUID.randomUUID();
        OutboundSaga saga = new OutboundSaga(sagaId, orderId,
                SagaStatus.CANCELLATION_REQUESTED, sagaId, null, T0, T0, 0L);
        sagaPersistence.save(saga);

        sweeper.sweep();

        assertThat(reEmitter.calls).containsExactly(
                new ReEmitCall(sagaId, "outbound.picking.cancelled"));
        assertThat(meterRegistry.counter("outbound.saga.sweeper.recovery.fired",
                "from_state", "CANCELLATION_REQUESTED").count()).isEqualTo(1.0);
    }

    @Test
    void shippedPastGraceReEmitsShippingConfirmed() {
        UUID sagaId = UUID.randomUUID();
        OutboundSaga saga = new OutboundSaga(sagaId, UUID.randomUUID(),
                SagaStatus.SHIPPED, sagaId, null, T0, T0, 0L);
        sagaPersistence.save(saga);

        sweeper.sweep();

        assertThat(reEmitter.calls).containsExactly(
                new ReEmitCall(sagaId, "outbound.shipping.confirmed"));
        assertThat(meterRegistry.counter("outbound.saga.sweeper.recovery.fired",
                "from_state", "SHIPPED").count()).isEqualTo(1.0);
    }

    @Test
    void freshSagaWithinGraceIsNotReEmitted() {
        // saga last-transitioned just 60s ago — still inside the 5-min grace.
        Instant recent = TICK.minusSeconds(60);
        OutboundSaga saga = new OutboundSaga(UUID.randomUUID(), UUID.randomUUID(),
                SagaStatus.REQUESTED, UUID.randomUUID(), null, recent, recent, 0L);
        sagaPersistence.save(saga);

        sweeper.sweep();

        assertThat(reEmitter.calls).isEmpty();
        OutboundSaga reloaded = sagaPersistence.findById(saga.sagaId()).orElseThrow();
        assertThat(reloaded.reEmitCount()).isZero();
    }

    @Test
    void terminalSagaIsNotReEmitted() {
        // COMPLETED saga should never be visible to findStuck — but even if
        // it were, the handler's state guard would skip it.
        UUID sagaId = UUID.randomUUID();
        OutboundSaga saga = new OutboundSaga(sagaId, UUID.randomUUID(),
                SagaStatus.COMPLETED, sagaId, null, T0, T0, 0L);
        sagaPersistence.save(saga);

        sweeper.sweep();

        assertThat(reEmitter.calls).isEmpty();
    }

    @Test
    void capExhaustionTransitionsToStuckAndEmitsAlert() {
        UUID sagaId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        // Saga has already had MAX_ATTEMPTS - 1 = 4 re-emissions; one more
        // should hit the cap.
        OutboundSaga saga = new OutboundSaga(sagaId, orderId,
                SagaStatus.REQUESTED, sagaId, null, T0, T0, 0L,
                MAX_ATTEMPTS - 1);
        sagaPersistence.save(saga);

        sweeper.sweep();

        OutboundSaga reloaded = sagaPersistence.findById(sagaId).orElseThrow();
        assertThat(reloaded.status()).isEqualTo(SagaStatus.STUCK_RECOVERY_FAILED);
        assertThat(reloaded.reEmitCount()).isEqualTo(MAX_ATTEMPTS);
        assertThat(reloaded.failureReason())
                .isEqualTo("saga_recovery_attempts_exhausted");

        // Alert event fired.
        assertThat(outboxWriter.published).hasSize(1);
        assertThat(outboxWriter.published.get(0))
                .isInstanceOfSatisfying(SagaRecoveryExhaustedEvent.class, alert -> {
                    assertThat(alert.sagaId()).isEqualTo(sagaId);
                    assertThat(alert.orderId()).isEqualTo(orderId);
                    assertThat(alert.stuckState()).isEqualTo("REQUESTED");
                    assertThat(alert.reEmitCount()).isEqualTo(MAX_ATTEMPTS);
                    assertThat(alert.failureReason())
                            .isEqualTo("saga_recovery_attempts_exhausted");
                });

        // No outbox re-emission on the exhaustion tick — the alert is the
        // sole outbox write.
        assertThat(reEmitter.calls).isEmpty();

        assertThat(meterRegistry.counter("outbound.saga.sweeper.exhausted.count",
                "from_state", "REQUESTED").count()).isEqualTo(1.0);
    }

    @Test
    void successiveTicksAccumulateAttemptsWhenClockAdvances() {
        // After each re-emission the saga's lastTransitionAt advances to
        // the current clock — so a *second* tick fires only if the clock
        // has moved another grace-period forward (the sweeper deliberately
        // gives inventory the full grace window between re-emits).
        OutboundSaga saga = OutboundSaga.newRequested(UUID.randomUUID(), UUID.randomUUID(), T0);
        sagaPersistence.save(saga);

        // Tick 1: clock at TICK (T0+10min); saga lastTransitionAt = T0; stuck.
        sweeper.sweep();
        // After tick 1, saga.lastTransitionAt = TICK; reEmitCount = 1.

        // Tick 2: advance clock past grace window so saga is stuck again.
        Instant tick2 = TICK.plusSeconds(GRACE_SECONDS + 1);
        sagaPersistence.clock = Clock.fixed(tick2, ZoneOffset.UTC);
        Clock clock2 = Clock.fixed(tick2, ZoneOffset.UTC);
        SagaRecoveryHandler handler2 = new SagaRecoveryHandler(sagaPersistence, reEmitter,
                outboxWriter, meterRegistry, clock2);
        SagaSweeper sweeper2 = new SagaSweeper(sagaPersistence, handler2, meterRegistry,
                GRACE_SECONDS, 100, MAX_ATTEMPTS);
        sweeper2.sweep();

        // Tick 3: same again.
        Instant tick3 = tick2.plusSeconds(GRACE_SECONDS + 1);
        sagaPersistence.clock = Clock.fixed(tick3, ZoneOffset.UTC);
        Clock clock3 = Clock.fixed(tick3, ZoneOffset.UTC);
        SagaRecoveryHandler handler3 = new SagaRecoveryHandler(sagaPersistence, reEmitter,
                outboxWriter, meterRegistry, clock3);
        SagaSweeper sweeper3 = new SagaSweeper(sagaPersistence, handler3, meterRegistry,
                GRACE_SECONDS, 100, MAX_ATTEMPTS);
        sweeper3.sweep();

        OutboundSaga reloaded = sagaPersistence.findById(saga.sagaId()).orElseThrow();
        assertThat(reloaded.reEmitCount()).isEqualTo(3);
        assertThat(meterRegistry.counter("outbound.saga.sweeper.recovery.fired",
                "from_state", "REQUESTED").count()).isEqualTo(3.0);
        assertThat(reEmitter.calls).hasSize(3);
    }

    @Test
    void doubleTickInsideGracePeriodOnlyEmitsOnce() {
        // Sanity: if the second tick happens *before* the grace window has
        // elapsed since the re-emission, the saga is no longer "stuck" and
        // the second tick is a no-op. This is the desired behaviour — the
        // sweeper deliberately gives inventory the full grace window
        // between re-emits.
        OutboundSaga saga = OutboundSaga.newRequested(UUID.randomUUID(), UUID.randomUUID(), T0);
        sagaPersistence.save(saga);

        sweeper.sweep();
        sweeper.sweep();
        sweeper.sweep();

        OutboundSaga reloaded = sagaPersistence.findById(saga.sagaId()).orElseThrow();
        assertThat(reloaded.reEmitCount()).isEqualTo(1);
        assertThat(reEmitter.calls).hasSize(1);
    }

    @Test
    void missingOriginalOutboxRowStillBumpsCounter() {
        // Defensive: re-emitter returns false (no original row) — counter
        // still advances so the saga eventually reaches STUCK_RECOVERY_FAILED.
        reEmitter.alwaysFail = true;
        OutboundSaga saga = OutboundSaga.newRequested(UUID.randomUUID(), UUID.randomUUID(), T0);
        sagaPersistence.save(saga);

        sweeper.sweep();

        OutboundSaga reloaded = sagaPersistence.findById(saga.sagaId()).orElseThrow();
        assertThat(reloaded.reEmitCount()).isEqualTo(1);
        assertThat(reloaded.status()).isEqualTo(SagaStatus.REQUESTED);
    }

    @Test
    void multipleStatesSweptInSingleTick() {
        OutboundSaga req = OutboundSaga.newRequested(UUID.randomUUID(), UUID.randomUUID(), T0);
        sagaPersistence.save(req);
        UUID cancSagaId = UUID.randomUUID();
        sagaPersistence.save(new OutboundSaga(cancSagaId, UUID.randomUUID(),
                SagaStatus.CANCELLATION_REQUESTED, cancSagaId, null, T0, T0, 0L));
        UUID shipSagaId = UUID.randomUUID();
        sagaPersistence.save(new OutboundSaga(shipSagaId, UUID.randomUUID(),
                SagaStatus.SHIPPED, shipSagaId, null, T0, T0, 0L));

        sweeper.sweep();

        assertThat(reEmitter.calls).hasSize(3);
        assertThat(reEmitter.calls).extracting(ReEmitCall::eventType)
                .containsExactlyInAnyOrder(
                        "outbound.picking.requested",
                        "outbound.picking.cancelled",
                        "outbound.shipping.confirmed");
    }

    // -- helpers ----------------------------------------------------------

    private record ReEmitCall(UUID sagaId, String eventType) {}

    private static final class RecordingReEmitter implements OutboxReEmitterPort {
        final List<ReEmitCall> calls = new ArrayList<>();
        boolean alwaysFail;

        @Override
        public boolean reEmit(UUID aggregateId, String eventType) {
            if (alwaysFail) {
                return false;
            }
            calls.add(new ReEmitCall(aggregateId, eventType));
            return true;
        }
    }
}
