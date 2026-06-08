package com.wms.outbound.application.port.in;

import com.wms.outbound.application.result.SagaResult;
import java.util.Optional;
import java.util.UUID;

/**
 * Read-side in-port for the {@code OutboundSaga} lookup by order id
 * (outbound-service-api.md §5.1 — {@code GET /orders/{id}/saga}). Lets the REST
 * layer read saga state without importing the persistence out-port.
 */
public interface QuerySagaUseCase {

    Optional<SagaResult> findByOrderId(UUID orderId);
}
