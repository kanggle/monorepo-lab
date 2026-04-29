package com.wms.outbound.application.port.in;

import com.wms.outbound.application.command.SealPackingUnitCommand;
import com.wms.outbound.application.result.PackingUnitResult;

/**
 * In-port for {@code PATCH /api/v1/outbound/packing-units/{id}} (seal=true).
 * Transitions the unit from {@code OPEN} to {@code SEALED}.
 */
public interface SealPackingUnitUseCase {

    PackingUnitResult seal(SealPackingUnitCommand command);
}
