package com.wms.outbound.application.service.fakes;

import com.wms.outbound.application.port.out.PickingConfirmationPersistencePort;
import com.wms.outbound.domain.model.PickingConfirmation;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class FakePickingConfirmationPersistencePort implements PickingConfirmationPersistencePort {

    private final Map<UUID, PickingConfirmation> byPickingRequestId = new HashMap<>();
    public int saveCalls;

    @Override
    public PickingConfirmation save(PickingConfirmation confirmation) {
        saveCalls++;
        byPickingRequestId.put(confirmation.getPickingRequestId(), confirmation);
        return confirmation;
    }

    @Override
    public Optional<PickingConfirmation> findByPickingRequestId(UUID pickingRequestId) {
        return Optional.ofNullable(byPickingRequestId.get(pickingRequestId));
    }
}
