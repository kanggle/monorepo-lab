package com.wms.outbound.application.port.out;

import com.wms.outbound.domain.model.PickingConfirmation;
import java.util.Optional;
import java.util.UUID;

/**
 * Out-port for {@link PickingConfirmation} aggregate persistence.
 */
public interface PickingConfirmationPersistencePort {

    PickingConfirmation save(PickingConfirmation confirmation);

    Optional<PickingConfirmation> findByPickingRequestId(UUID pickingRequestId);
}
