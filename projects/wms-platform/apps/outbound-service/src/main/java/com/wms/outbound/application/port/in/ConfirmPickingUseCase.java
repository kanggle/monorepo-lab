package com.wms.outbound.application.port.in;

import com.wms.outbound.application.command.ConfirmPickingCommand;
import com.wms.outbound.application.result.PickingConfirmationResult;

/**
 * In-port for the operator-issued pick-confirmation step.
 *
 * <p>Validates {@code Order.status == PICKING}, that the saga is in
 * {@code RESERVED}, that quantities match {@code OrderLine.qtyOrdered},
 * and that LOT-tracked SKUs supply a {@code lotId}. On success creates the
 * {@code PickingConfirmation}, advances the order to {@code PICKED}, advances
 * the saga to {@code PICKING_CONFIRMED}, and writes
 * {@code outbound.picking.completed} to the outbox in one TX.
 *
 * <p>See {@code outbound-service-api.md} §2.3 and AC-05.
 */
public interface ConfirmPickingUseCase {

    PickingConfirmationResult confirm(ConfirmPickingCommand command);
}
