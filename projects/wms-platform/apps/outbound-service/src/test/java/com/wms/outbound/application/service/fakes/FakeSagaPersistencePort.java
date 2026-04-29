package com.wms.outbound.application.service.fakes;

import com.wms.outbound.application.port.out.SagaPersistencePort;
import com.wms.outbound.domain.model.OutboundSaga;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FakeSagaPersistencePort implements SagaPersistencePort {

    private final Map<UUID, OutboundSaga> store = new HashMap<>();

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

    public int sagaCount() {
        return store.size();
    }
}
