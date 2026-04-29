package com.wms.outbound.application.port.in;

import com.wms.outbound.application.command.ConfirmPackingCommand;
import com.wms.outbound.application.result.OrderResult;

/**
 * In-port for {@code POST /api/v1/outbound/orders/{id}/packing/confirm}.
 *
 * <p>Validates that all packing units are sealed and that the sum of their
 * line quantities covers each {@code OrderLine.qtyOrdered}. On success
 * advances the order to {@code PACKED}, advances the saga to
 * {@code PACKING_CONFIRMED}, and writes {@code outbound.packing.completed}
 * to the outbox in one TX.
 */
public interface ConfirmPackingUseCase {

    OrderResult confirm(ConfirmPackingCommand command);
}
