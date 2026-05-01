package com.wms.outbound.application.service.fakes;

import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.domain.model.OutboundSaga;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FakeSagaPersistencePort implements SagaPersistencePort {

    private final Map<UUID, OutboundSaga> store = new HashMap<>();

    /**
     * Counter incremented every time {@link #findByOrderId} is called. Tests
     * use this to assert the list endpoint does not fall back to per-row
     * lookups (AC-03).
     */
    public int findByOrderIdCallCount;

    @Override
    public OutboundSaga save(OutboundSaga saga) {
        store.put(saga.sagaId(), saga);
        return saga;
    }

    @Override
    public Optional<OutboundSaga> findById(UUID sagaId) {
        return Optional.ofNullable(store.get(sagaId));
    }

    @Override
    public Optional<OutboundSaga> findByOrderId(UUID orderId) {
        findByOrderIdCallCount++;
        return store.values().stream()
                .filter(s -> s.orderId().equals(orderId))
                .findFirst();
    }

    @Override
    public Optional<OutboundSaga> findByPickingRequestId(UUID pickingRequestId) {
        return store.values().stream()
                .filter(s -> pickingRequestId.equals(s.pickingRequestId()))
                .findFirst();
    }

    @Override
    public Map<UUID, String> findSagaStatesByOrderIds(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> out = new HashMap<>();
        for (OutboundSaga s : store.values()) {
            if (orderIds.contains(s.orderId())) {
                out.put(s.orderId(), s.status().name());
            }
        }
        return out;
    }

    public int sagaCount() {
        return store.size();
    }
}
