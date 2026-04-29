package com.wms.outbound.application.port.in;

import com.wms.outbound.application.command.CreatePackingUnitCommand;
import com.wms.outbound.application.result.PackingUnitResult;

/**
 * In-port for {@code POST /api/v1/outbound/orders/{id}/packing-units}.
 * Allowed when {@code Order.status ∈ {PICKED, PACKING}}; first call
 * transitions the order from {@code PICKED} to {@code PACKING}.
 */
public interface CreatePackingUnitUseCase {

    PackingUnitResult create(CreatePackingUnitCommand command);
}
