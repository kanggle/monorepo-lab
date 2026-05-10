package com.wms.outbound.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.outbound.domain.exception.StateTransitionInvalidException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the {@link OutboundSaga} sweeper-driven transitions
 * introduced in TASK-BE-050:
 *
 * <ul>
 *   <li>{@link OutboundSaga#recordReEmission(Instant)} — counter bump,
 *       no state change, last-transition advances</li>
 *   <li>{@link OutboundSaga#markStuckRecoveryFailed(String, Instant)} —
 *       allowed predecessors, terminal idempotency, illegal predecessor
 *       throws</li>
 * </ul>
 */
class OutboundSagaSweeperTransitionTest {

    private static final Instant T0 = Instant.parse("2026-05-10T10:00:00Z");
    private static final Instant T1 = Instant.parse("2026-05-10T10:05:00Z");
    private static final Instant T2 = Instant.parse("2026-05-10T10:10:00Z");

    private static OutboundSaga newRequested() {
        return OutboundSaga.newRequested(UUID.randomUUID(), UUID.randomUUID(), T0);
    }

    @Test
    void recordReEmissionBumpsCounterAndLastTransition() {
        OutboundSaga saga = newRequested();

        saga.recordReEmission(T1);

        assertThat(saga.reEmitCount()).isEqualTo(1);
        assertThat(saga.lastTransitionAt()).isEqualTo(T1);
        // State must NOT change on re-emission.
        assertThat(saga.status()).isEqualTo(SagaStatus.REQUESTED);
    }

    @Test
    void recordReEmissionMonotonic() {
        OutboundSaga saga = newRequested();

        saga.recordReEmission(T1);
        saga.recordReEmission(T2);

        assertThat(saga.reEmitCount()).isEqualTo(2);
    }

    @Test
    void markStuckFromRequestedSucceeds() {
        OutboundSaga saga = newRequested();

        saga.markStuckRecoveryFailed("saga_recovery_attempts_exhausted", T2);

        assertThat(saga.status()).isEqualTo(SagaStatus.STUCK_RECOVERY_FAILED);
        assertThat(saga.failureReason()).isEqualTo("saga_recovery_attempts_exhausted");
        assertThat(saga.lastTransitionAt()).isEqualTo(T2);
    }

    @Test
    void markStuckFromCancellationRequestedSucceeds() {
        OutboundSaga saga = newRequested();
        saga.onInventoryReserved(T1);
        saga.requestCancellation(T1);
        assertThat(saga.status()).isEqualTo(SagaStatus.CANCELLATION_REQUESTED);

        saga.markStuckRecoveryFailed("saga_recovery_attempts_exhausted", T2);

        assertThat(saga.status()).isEqualTo(SagaStatus.STUCK_RECOVERY_FAILED);
    }

    @Test
    void markStuckFromShippedSucceeds() {
        // Hand-construct a saga in SHIPPED — easier than driving the full
        // happy path through ConfirmShippingService for a unit test.
        OutboundSaga saga = new OutboundSaga(
                UUID.randomUUID(), UUID.randomUUID(),
                SagaStatus.SHIPPED, UUID.randomUUID(), null, T0, T0, 0L);

        saga.markStuckRecoveryFailed("saga_recovery_attempts_exhausted", T2);

        assertThat(saga.status()).isEqualTo(SagaStatus.STUCK_RECOVERY_FAILED);
    }

    @Test
    void markStuckFromTerminalIsIdempotent() {
        OutboundSaga saga = newRequested();
        saga.markStuckRecoveryFailed("first", T1);

        // Second call must not raise.
        saga.markStuckRecoveryFailed("second", T2);

        assertThat(saga.status()).isEqualTo(SagaStatus.STUCK_RECOVERY_FAILED);
        // First reason and timestamp preserved.
        assertThat(saga.failureReason()).isEqualTo("first");
        assertThat(saga.lastTransitionAt()).isEqualTo(T1);
    }

    @Test
    void markStuckFromCompletedThrows() {
        OutboundSaga saga = new OutboundSaga(
                UUID.randomUUID(), UUID.randomUUID(),
                SagaStatus.COMPLETED, UUID.randomUUID(), null, T0, T0, 0L);

        assertThatThrownBy(() -> saga.markStuckRecoveryFailed("x", T2))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void markStuckFromReservedThrows() {
        OutboundSaga saga = newRequested();
        saga.onInventoryReserved(T1);
        assertThat(saga.status()).isEqualTo(SagaStatus.RESERVED);

        // RESERVED is a non-sweepable state — only REQUESTED, SHIPPED,
        // CANCELLATION_REQUESTED qualify. Calling markStuck from any
        // other transitional state must raise.
        assertThatThrownBy(() -> saga.markStuckRecoveryFailed("x", T2))
                .isInstanceOf(StateTransitionInvalidException.class);
    }
}
