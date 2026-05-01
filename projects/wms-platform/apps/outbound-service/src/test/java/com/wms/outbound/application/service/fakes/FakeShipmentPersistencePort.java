package com.wms.outbound.application.service.fakes;

import com.wms.outbound.application.port.out.ShipmentPersistencePort;
import com.wms.outbound.domain.model.Shipment;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FakeShipmentPersistencePort implements ShipmentPersistencePort {

    private final Map<UUID, Shipment> store = new HashMap<>();
    public int saveCalls;

    @Override
    public Shipment save(Shipment shipment) {
        saveCalls++;
        store.put(shipment.getId(), shipment);
        return shipment;
    }

    @Override
    public Optional<Shipment> findById(UUID id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public Optional<Shipment> findByOrderId(UUID orderId) {
        return store.values().stream()
                .filter(s -> s.getOrderId().equals(orderId))
                .findFirst();
    }

    public int count() {
        return store.size();
    }
}
