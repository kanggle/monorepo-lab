package com.wms.outbound.application.port.out;

import com.wms.outbound.domain.model.PickingRequest;
import java.util.Optional;
import java.util.UUID;

/**
 * Out-port for {@link PickingRequest} aggregate persistence.
 */
public interface PickingPersistencePort {

    PickingRequest save(PickingRequest request);

    Optional<PickingRequest> findById(UUID id);

    Optional<PickingRequest> findByOrderId(UUID orderId);
}
