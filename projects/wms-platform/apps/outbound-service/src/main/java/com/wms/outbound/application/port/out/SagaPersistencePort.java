package com.wms.outbound.application.port.out;

import com.wms.outbound.domain.model.OutboundSaga;
import java.util.Optional;
import java.util.UUID;

/**
 * Out-port for {@link OutboundSaga} aggregate persistence.
 */
public interface SagaPersistencePort {

    OutboundSaga save(OutboundSaga saga);

    Optional<OutboundSaga> findById(UUID sagaId);

    Optional<OutboundSaga> findByOrderId(UUID orderId);

    /**
     * Lookup by the cross-service correlation key. Inventory replies carry
     * {@code pickingRequestId}, which the consumer uses to find the right
     * saga without knowing the saga id directly.
     */
    Optional<OutboundSaga> findByPickingRequestId(UUID pickingRequestId);
}
