package com.wms.outbound.adapter.out.persistence.adapter;

import com.wms.outbound.adapter.out.persistence.entity.OutboundSagaEntity;
import com.wms.outbound.adapter.out.persistence.repository.OutboundSagaRepository;
import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.domain.model.OutboundSaga;
import com.wms.outbound.domain.model.SagaStatus;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persistence adapter for the {@link OutboundSaga} aggregate. Implements
 * {@link SagaPersistencePort}.
 */
@Component
public class SagaPersistenceAdapter implements SagaPersistencePort {

    private final OutboundSagaRepository repo;
    private final Clock clock;

    public SagaPersistenceAdapter(OutboundSagaRepository repo, Clock clock) {
        this.repo = repo;
        this.clock = clock;
    }

    @Override
    @Transactional
    public OutboundSaga save(OutboundSaga saga) {
        Optional<OutboundSagaEntity> existing = repo.findById(saga.sagaId());
        OutboundSagaEntity entity;
        if (existing.isEmpty()) {
            entity = new OutboundSagaEntity(
                    saga.sagaId(),
                    saga.orderId(),
                    saga.status().name(),
                    saga.pickingRequestId(),
                    saga.failureReason(),
                    saga.startedAt(),
                    saga.lastTransitionAt(),
                    saga.reEmitCount());
        } else {
            entity = existing.get();
            entity.setStatus(saga.status().name());
            entity.setFailureReason(saga.failureReason());
            entity.setUpdatedAt(saga.lastTransitionAt());
            entity.setPickingRequestId(saga.pickingRequestId());
            entity.setReEmitCount(saga.reEmitCount());
        }
        entity = repo.save(entity);
        return toDomain(entity);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OutboundSaga> findById(UUID sagaId) {
        return repo.findById(sagaId).map(SagaPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OutboundSaga> findByOrderId(UUID orderId) {
        return repo.findByOrderId(orderId).map(SagaPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<OutboundSaga> findByPickingRequestId(UUID pickingRequestId) {
        return repo.findByPickingRequestId(pickingRequestId)
                .map(SagaPersistenceAdapter::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public List<OutboundSaga> findStuck(SagaStatus status, Duration gracePeriod, int limit) {
        if (limit <= 0) {
            return List.of();
        }
        long graceSeconds = gracePeriod == null ? 0L : Math.max(0L, gracePeriod.getSeconds());
        List<OutboundSagaEntity> rows = repo.findStuckByStatusUsingDbClock(
                status.name(), graceSeconds, limit);
        List<OutboundSaga> out = new ArrayList<>(rows.size());
        for (OutboundSagaEntity e : rows) {
            out.add(toDomain(e));
        }
        return out;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> findSagaStatesByOrderIds(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> result = new HashMap<>();
        for (OutboundSagaEntity e : repo.findByOrderIdIn(orderIds)) {
            result.put(e.getOrderId(), e.getStatus());
        }
        return result;
    }

    private static OutboundSaga toDomain(OutboundSagaEntity e) {
        return new OutboundSaga(
                e.getId(),
                e.getOrderId(),
                SagaStatus.valueOf(e.getStatus()),
                e.getPickingRequestId(),
                e.getFailureReason(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getVersion(),
                e.getReEmitCount());
    }
}
