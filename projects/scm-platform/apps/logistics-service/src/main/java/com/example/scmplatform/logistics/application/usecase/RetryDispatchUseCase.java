package com.example.scmplatform.logistics.application.usecase;

import com.example.scmplatform.logistics.application.port.outbound.DispatchPersistencePort;
import com.example.scmplatform.logistics.domain.error.DispatchNotFoundException;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Operator recovery: re-drive a dispatch by id (the relocation target of the wms
 * {@code :retry-tms-notify} endpoint, ADR-053 §D8).
 *
 * <p>Naturally idempotent (S2): re-invoking an already-{@code DISPATCHED} shipment returns the
 * cached ack with no vendor call (via {@link DispatchShipmentUseCase}); a {@code DISPATCH_FAILED}
 * shipment is re-driven with the same {@code Idempotency-Key}.
 */
@Service
public class RetryDispatchUseCase {

    private final DispatchPersistencePort persistencePort;
    private final DispatchShipmentUseCase dispatchShipmentUseCase;

    public RetryDispatchUseCase(DispatchPersistencePort persistencePort,
                                DispatchShipmentUseCase dispatchShipmentUseCase) {
        this.persistencePort = persistencePort;
        this.dispatchShipmentUseCase = dispatchShipmentUseCase;
    }

    @Transactional
    public Dispatch retry(UUID dispatchId) {
        Dispatch dispatch = persistencePort.findById(dispatchId)
                .orElseThrow(() -> new DispatchNotFoundException(dispatchId));
        return dispatchShipmentUseCase.dispatch(dispatch);
    }
}
