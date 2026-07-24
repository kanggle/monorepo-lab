package com.wms.outbound.application.port.in;

import com.wms.outbound.application.command.ConfirmShippingCommand;
import com.wms.outbound.application.result.ShipmentResult;

/**
 * In-port for {@code POST /api/v1/outbound/orders/{id}/shipping/confirm}.
 *
 * <p>Validates {@code Order.status == PACKED}. On success creates the
 * {@code Shipment} (auto-generated {@code shipment_no}), advances the order to
 * {@code SHIPPED}, the saga to {@code SHIPPED}, and writes
 * {@code outbound.shipping.confirmed} to the outbox in one TX. Carrier dispatch
 * is driven downstream by the scm {@code logistics-service} off that event
 * (ADR-MONO-053 §D8).
 */
public interface ConfirmShippingUseCase {

    ShipmentResult confirm(ConfirmShippingCommand command);
}
