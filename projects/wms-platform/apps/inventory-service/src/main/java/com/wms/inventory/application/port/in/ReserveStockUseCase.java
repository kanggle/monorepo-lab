package com.wms.inventory.application.port.in;

import com.wms.inventory.application.command.ReserveStockCommand;
import com.wms.inventory.application.result.ReservationView;

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

    /** Outcome of the event-path reserve. */
    enum ReserveOutcome { RESERVED, BACKORDERED }
}
