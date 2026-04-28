package com.wms.inventory.application.port.in;

import com.wms.inventory.application.command.ReserveStockCommand;
import com.wms.inventory.application.result.ReservationView;

/**
 * In-port for the W4 reserve flow. Called by REST {@code POST /reservations}
 * and by {@code PickingRequestedConsumer} (after EventDedupe).
 */
public interface ReserveStockUseCase {

    ReservationView reserve(ReserveStockCommand command);
}
