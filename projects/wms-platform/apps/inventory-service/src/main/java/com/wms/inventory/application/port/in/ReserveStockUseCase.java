package com.wms.inventory.application.port.in;

import com.wms.inventory.application.command.ReserveStockCommand;
import com.wms.inventory.application.result.ReservationView;
import java.util.List;
import java.util.UUID;

/**
 * In-port for the W4 reserve flow. Called by REST {@code POST /reservations}
 * and by {@code PickingRequestedConsumer} (after EventDedupe).
 */
public interface ReserveStockUseCase {

    ReservationView reserve(ReserveStockCommand command);

    /**
     * Event-path reserve (TASK-MONO-196, ADR-MONO-022 §D4). Pre-checks
     * availability and, on a shortfall, emits {@code inventory.reserve.failed}
     * via the outbox <em>instead of throwing</em> — so the consumer's
     * transaction commits and the eventId dedupe row is retained (no DLT, no
     * redelivery loop). Returns {@link ReserveOutcome#RESERVED} on success or
     * {@link ReserveOutcome#BACKORDERED} on shortfall. The REST
     * {@link #reserve} path is unchanged (still throws
     * {@code InsufficientStockException} → 422).
     */
    ReserveOutcome reserveForPickingEvent(ReserveStockCommand command);

    /**
     * Emit {@code inventory.reserve.failed} directly for a picking request that
     * cannot be reserved because <em>no inventory row resolves</em> to the
     * requested natural key(s) — there is no {@code inventoryId} to feed
     * {@link #reserveForPickingEvent}. Used by {@code PickingRequestedConsumer}
     * for the zero-stock edge: the order still backorders rather than NPEing or
     * DLT-looping. No mutation; one outbox row.
     *
     * @param pickingRequestId the reservation/picking-request id (also the
     *        failure event's aggregate id + partition key)
     * @param actorId the consumer's system actor
     * @param shortfalls the under-covered lines (natural-key identity +
     *        requested/available quantities)
     */
    void signalReserveFailed(UUID pickingRequestId, String actorId, List<Shortfall> shortfalls);

    /** Outcome of the event-path reserve. */
    enum ReserveOutcome { RESERVED, BACKORDERED }

    /**
     * A reservation-line shortfall resolved from natural-key identity (used when
     * no concrete {@code inventory} row id is available, e.g. zero stock).
     * {@code inventoryId} is nullable — the failure event carries it only as a
     * diagnostic hint; outbound correlates the backorder by {@code
     * pickingRequestId}.
     */
    record Shortfall(UUID inventoryId, UUID skuId, UUID lotId, UUID locationId,
                     int qtyRequested, int qtyAvailable) {
    }
}
