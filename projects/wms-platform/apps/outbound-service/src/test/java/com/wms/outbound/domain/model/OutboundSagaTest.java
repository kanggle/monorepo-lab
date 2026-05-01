package com.wms.outbound.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.wms.outbound.domain.exception.StateTransitionInvalidException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class OutboundSagaTest {

    private static final Instant T0 = Instant.parse("2026-04-29T10:00:00Z");

    @Test
    void requestedToReservedAdvancesOnInventoryReserved() {
        OutboundSaga saga = newRequested();
        saga.onInventoryReserved(T0);
        assertThat(saga.status()).isEqualTo(SagaStatus.RESERVED);
    }

    @Test
    void redeliveredReservedIsIdempotent() {
        OutboundSaga saga = newRequested();
        saga.onInventoryReserved(T0);
        // Re-delivery — must not throw.
        saga.onInventoryReserved(T0);
        assertThat(saga.status()).isEqualTo(SagaStatus.RESERVED);
    }

    @Test
    void onInventoryReservedFromUnexpectedStateThrows() {
        OutboundSaga saga = newRequested();
        saga.cancelImmediately(T0); // CANCELLED
        assertThatThrownBy(() -> saga.onInventoryReserved(T0))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void onReserveFailedFromRequestedAdvancesToReserveFailed() {
        OutboundSaga saga = newRequested();
        saga.onReserveFailed("INSUFFICIENT_STOCK", T0);
        assertThat(saga.status()).isEqualTo(SagaStatus.RESERVE_FAILED);
        assertThat(saga.failureReason()).isEqualTo("INSUFFICIENT_STOCK");
    }

    @Test
    void requestCancellationFromReservedSetsCancellationRequested() {
        OutboundSaga saga = newRequested();
        saga.onInventoryReserved(T0);
        saga.requestCancellation(T0);
        assertThat(saga.status()).isEqualTo(SagaStatus.CANCELLATION_REQUESTED);
    }

    @Test
    void onInventoryReleasedFromCancellationRequestedSettlesCancelled() {
        OutboundSaga saga = newRequested();
        saga.onInventoryReserved(T0);
        saga.requestCancellation(T0);
        saga.onInventoryReleased(T0);
        assertThat(saga.status()).isEqualTo(SagaStatus.CANCELLED);
    }

    @Test
    void onInventoryConfirmedFromShippedAdvancesCompleted() {
        OutboundSaga saga = sagaIn(SagaStatus.SHIPPED);
        saga.onInventoryConfirmed(T0);
        assertThat(saga.status()).isEqualTo(SagaStatus.COMPLETED);
    }

    @Test
    void onInventoryConfirmedFromUnexpectedStateThrows() {
        OutboundSaga saga = newRequested();
        assertThatThrownBy(() -> saga.onInventoryConfirmed(T0))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void requestCancellationOnTerminalThrows() {
        OutboundSaga saga = sagaIn(SagaStatus.COMPLETED);
        assertThatThrownBy(() -> saga.requestCancellation(T0))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void cancelImmediatelyFromRequestedSettlesCancelled() {
        OutboundSaga saga = newRequested();
        saga.cancelImmediately(T0);
        assertThat(saga.status()).isEqualTo(SagaStatus.CANCELLED);
    }

    // -- TASK-BE-038 transitions ---------------------------------------

    @Test
    void onPickingConfirmedFromReservedAdvances() {
        OutboundSaga saga = newRequested();
        saga.onInventoryReserved(T0);
        saga.onPickingConfirmed(T0);
        assertThat(saga.status()).isEqualTo(SagaStatus.PICKING_CONFIRMED);
    }

    @Test
    void onPickingConfirmedFromUnexpectedStateThrows() {
        OutboundSaga saga = newRequested();
        assertThatThrownBy(() -> saga.onPickingConfirmed(T0))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void onPickingConfirmedReDeliveryIsIdempotent() {
        OutboundSaga saga = sagaIn(SagaStatus.PICKING_CONFIRMED);
        saga.onPickingConfirmed(T0); // no throw
        assertThat(saga.status()).isEqualTo(SagaStatus.PICKING_CONFIRMED);
    }

    @Test
    void onPackingConfirmedFromPickingConfirmedAdvances() {
        OutboundSaga saga = sagaIn(SagaStatus.PICKING_CONFIRMED);
        saga.onPackingConfirmed(T0);
        assertThat(saga.status()).isEqualTo(SagaStatus.PACKING_CONFIRMED);
    }

    @Test
    void onPackingConfirmedFromUnexpectedStateThrows() {
        OutboundSaga saga = sagaIn(SagaStatus.RESERVED);
        assertThatThrownBy(() -> saga.onPackingConfirmed(T0))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void onShippingConfirmedFromPackingConfirmedAdvances() {
        OutboundSaga saga = sagaIn(SagaStatus.PACKING_CONFIRMED);
        saga.onShippingConfirmed(T0);
        assertThat(saga.status()).isEqualTo(SagaStatus.SHIPPED);
    }

    @Test
    void onShippingConfirmedFromUnexpectedStateThrows() {
        OutboundSaga saga = newRequested();
        assertThatThrownBy(() -> saga.onShippingConfirmed(T0))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void onTmsNotifyFailedFromShippedAdvancesToShippedNotNotified() {
        OutboundSaga saga = sagaIn(SagaStatus.SHIPPED);
        saga.onTmsNotifyFailed("timeout", T0);
        assertThat(saga.status()).isEqualTo(SagaStatus.SHIPPED_NOT_NOTIFIED);
        assertThat(saga.failureReason()).isEqualTo("timeout");
    }

    @Test
    void onTmsNotifyFailedFromUnexpectedStateThrows() {
        OutboundSaga saga = sagaIn(SagaStatus.RESERVED);
        assertThatThrownBy(() -> saga.onTmsNotifyFailed("x", T0))
                .isInstanceOf(StateTransitionInvalidException.class);
    }

    @Test
    void onTmsNotifyFailedReDeliveryIsIdempotent() {
        OutboundSaga saga = sagaIn(SagaStatus.SHIPPED_NOT_NOTIFIED);
        saga.onTmsNotifyFailed("x", T0); // no throw
        assertThat(saga.status()).isEqualTo(SagaStatus.SHIPPED_NOT_NOTIFIED);
    }

    private static OutboundSaga newRequested() {
        return OutboundSaga.newRequested(UUID.randomUUID(), UUID.randomUUID(), T0);
    }

    private static OutboundSaga sagaIn(SagaStatus status) {
        UUID sagaId = UUID.randomUUID();
        return new OutboundSaga(sagaId, UUID.randomUUID(), status,
                sagaId, null, T0, T0, 0L);
    }
}
