package com.wms.outbound.application.port.out;

import com.wms.outbound.domain.model.Shipment;
import java.util.Optional;
import java.util.UUID;

/**
 * Out-port for {@link Shipment} aggregate persistence.
 */
public interface ShipmentPersistencePort {

    Shipment save(Shipment shipment);

    Optional<Shipment> findById(UUID id);

    Optional<Shipment> findByOrderId(UUID orderId);
}
