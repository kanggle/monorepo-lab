package com.wms.outbound.application.port.out;

import com.wms.outbound.domain.model.OutboundSaga;
import java.util.Collection;
import java.util.Map;
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

    /**
     * Bulk lookup of saga states keyed by orderId. Used by the list endpoint
     * to avoid the per-row {@link #findByOrderId} N+1 pattern. Returns an
     * empty map when {@code orderIds} is empty. Orders without a saga simply
     * have no entry in the returned map.
     */
    Map<UUID, String> findSagaStatesByOrderIds(Collection<UUID> orderIds);
}
