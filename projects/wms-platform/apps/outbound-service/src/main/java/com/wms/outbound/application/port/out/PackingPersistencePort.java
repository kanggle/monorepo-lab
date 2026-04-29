package com.wms.outbound.application.port.out;

import com.wms.outbound.domain.model.PackingUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Out-port for {@link PackingUnit} aggregate persistence.
 */
public interface PackingPersistencePort {

    PackingUnit save(PackingUnit unit);

    Optional<PackingUnit> findById(UUID id);

    List<PackingUnit> findByOrderId(UUID orderId);

    List<PackingUnit> findUnsealedByOrderId(UUID orderId);
}
