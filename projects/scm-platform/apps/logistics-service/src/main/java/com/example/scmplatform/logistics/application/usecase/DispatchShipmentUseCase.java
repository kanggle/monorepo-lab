package com.example.scmplatform.logistics.application.usecase;

import com.example.scmplatform.logistics.application.port.outbound.DispatchAck;
import com.example.scmplatform.logistics.application.port.outbound.DispatchPersistencePort;
import com.example.scmplatform.logistics.application.port.outbound.ShipmentDispatchPort;
import com.example.scmplatform.logistics.domain.error.ShipmentDispatchException;
import com.example.scmplatform.logistics.domain.model.CarrierCode;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import com.example.scmplatform.logistics.domain.model.TrackingNo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Drive a PENDING / DISPATCH_FAILED dispatch to the vendor via {@link ShipmentDispatchPort}
 * (ADR-053 §D2). With one vendor there is no {@code CarrierRouter} — the port is called
 * directly (introduced in BE-043/044).
 *
 * <p><b>Idempotent (S1/S2).</b> An already-{@code DISPATCHED} dispatch short-circuits with the
 * cached ack and <b>no vendor call</b> — the property the operator {@code :retry} relies on.
 * A vendor failure is <b>not</b> propagated: it is recorded {@code DISPATCH_FAILED} and
 * recovered via {@code :retry} (S5 — a failed carrier never blocks or rolls back).
 */
@Service
public class DispatchShipmentUseCase {

    private final ShipmentDispatchPort dispatchPort;
    private final DispatchPersistencePort persistencePort;

    public DispatchShipmentUseCase(ShipmentDispatchPort dispatchPort,
                                   DispatchPersistencePort persistencePort) {
        this.dispatchPort = dispatchPort;
        this.persistencePort = persistencePort;
    }

    /**
     * Dispatch (or re-dispatch) the shipment. Returns the resulting aggregate: DISPATCHED on
     * vendor success, DISPATCH_FAILED on vendor failure, or unchanged (cached ack) when already
     * DISPATCHED.
     */
    @Transactional
    public Dispatch dispatch(Dispatch dispatch) {
        if (!dispatch.getStatus().canDispatch()) {
            // Already DISPATCHED — idempotent, cached ack, no vendor call, no write.
            return dispatch;
        }
        try {
            DispatchAck ack = dispatchPort.dispatch(dispatch);
            dispatch.recordAck(
                    TrackingNo.of(ack.trackingNo()),
                    CarrierCode.of(ack.carrierCode()),
                    ack.vendor(),
                    Instant.now());
        } catch (ShipmentDispatchException e) {
            dispatch.recordFailure(e.getMessage(), Instant.now());
        }
        return persistencePort.save(dispatch);
    }
}
