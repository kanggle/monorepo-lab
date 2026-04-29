package com.wms.outbound.application.port.in;

import com.wms.outbound.application.result.PackingUnitResult;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-side in-port for the packing-unit lookup. Lets the REST layer resolve a
 * path-variable {@code packingUnitId} into the underlying {@code orderId}
 * without importing the persistence out-port (AC-04 of TASK-BE-040).
 */
public interface QueryPackingUnitUseCase {

    Optional<PackingUnitResult> findById(UUID packingUnitId);
}
