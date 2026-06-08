package com.wms.outbound.application.service;

import com.wms.outbound.application.port.in.QuerySagaUseCase;
import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.application.result.SagaResult;
import com.wms.outbound.domain.model.OutboundSaga;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-side query service backing {@link QuerySagaUseCase}
 * (outbound-service-api.md §5.1). The controller delegates the order-based saga
 * lookup here so the persistence out-port does not bleed into the adapter layer.
 */
@Service
public class SagaQueryService implements QuerySagaUseCase {

    private final SagaPersistencePort sagaPersistence;

    public SagaQueryService(SagaPersistencePort sagaPersistence) {
        this.sagaPersistence = sagaPersistence;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SagaResult> findByOrderId(UUID orderId) {
        return sagaPersistence.findByOrderId(orderId).map(SagaQueryService::toResult);
    }

    private static SagaResult toResult(OutboundSaga s) {
        return new SagaResult(
                s.sagaId(),
                s.orderId(),
                s.status().name(),
                s.failureReason(),
                s.startedAt(),
                s.lastTransitionAt(),
                s.version());
    }
}
