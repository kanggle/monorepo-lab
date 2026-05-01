package com.wms.outbound.application.port.in;

import com.wms.outbound.application.command.ReceiveOrderCommand;
import com.wms.outbound.application.result.OrderResult;

/**
 * In-port for the manual / webhook order ingest path. Creates the
 * {@code Order}, advances it to {@code PICKING}, creates an
 * {@code OutboundSaga} ({@code REQUESTED}), and writes
 * {@code outbound.order.received} + {@code outbound.picking.requested}
 * to the outbox in one TX.
 *
 * <p>See AC-04 of TASK-BE-037.
 */
public interface ReceiveOrderUseCase {

    OrderResult receive(ReceiveOrderCommand command);
}
