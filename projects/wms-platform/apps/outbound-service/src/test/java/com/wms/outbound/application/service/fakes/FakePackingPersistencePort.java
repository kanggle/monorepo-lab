package com.wms.outbound.application.service.fakes;

import com.wms.outbound.application.port.out.PackingPersistencePort;
import com.wms.outbound.domain.model.PackingUnit;
import com.wms.outbound.domain.model.PackingUnitStatus;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FakePackingPersistencePort implements PackingPersistencePort {

    private final Map<UUID, PackingUnit> store = new HashMap<>();

    @Override
    public PackingUnit save(PackingUnit unit) {
        store.put(unit.getId(), unit);
        return unit;
    }

    @Override
    public Optional<PackingUnit> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<PackingUnit> findByOrderId(UUID orderId) {
        List<PackingUnit> out = new ArrayList<>();
        for (PackingUnit u : store.values()) {
            if (u.getOrderId().equals(orderId)) {
                out.add(u);
            }
        }
        return out;
    }

    @Override
    public List<PackingUnit> findUnsealedByOrderId(UUID orderId) {
        List<PackingUnit> out = new ArrayList<>();
        for (PackingUnit u : store.values()) {
            if (u.getOrderId().equals(orderId) && u.getStatus() == PackingUnitStatus.OPEN) {
                out.add(u);
            }
        }
        return out;
    }

    public int count() {
        return store.size();
    }
}
