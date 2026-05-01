package com.wms.outbound.application.service.fakes;

import com.wms.outbound.application.port.out.PickingPersistencePort;
import com.wms.outbound.domain.model.PickingRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FakePickingPersistencePort implements PickingPersistencePort {

    private final Map<UUID, PickingRequest> store = new HashMap<>();

    @Override
    public PickingRequest save(PickingRequest request) {
        store.put(request.getId(), request);
        return request;
    }

    @Override
    public Optional<PickingRequest> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<PickingRequest> findByOrderId(UUID orderId) {
        return store.values().stream()
                .filter(r -> r.getOrderId().equals(orderId))
                .findFirst();
    }

    public int count() {
        return store.size();
    }
}
