package com.wms.outbound.application.port.in;

import com.wms.outbound.application.result.PickingRequestResult;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-side in-port for the picking-request lookup. Lets the REST layer
 * resolve the path-variable id into the underlying {@code orderId} without
 * importing the persistence out-port (AC-04 of TASK-BE-040).
 */
public interface QueryPickingRequestUseCase {

    Optional<PickingRequestResult> findById(UUID pickingRequestId);

    Optional<PickingRequestResult> findByOrderId(UUID orderId);
}
