package com.wms.inventory.application.port.in;

import com.wms.inventory.application.command.ReceiveStockCommand;

/**
 * In-port for the receive flow. Called by {@code PutawayCompletedConsumer}
 * after EventDedupe confirms first occurrence.
 *
 * <p>Effect per line: upsert Inventory at {@code (locationId, skuId, lotId)},
 * call {@code Inventory.receive(qty)}, write Movement row, write Outbox row.
 * All lines + Outbox commit in one transaction.
 */
public interface ReceiveStockUseCase {

    void receive(ReceiveStockCommand command);
}
