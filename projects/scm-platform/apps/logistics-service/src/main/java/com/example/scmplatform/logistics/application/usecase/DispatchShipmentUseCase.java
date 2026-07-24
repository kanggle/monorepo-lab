package com.example.scmplatform.logistics.application.usecase;

import com.example.scmplatform.logistics.application.port.outbound.DispatchAck;
import com.example.scmplatform.logistics.application.port.outbound.DispatchPersistencePort;
import com.example.scmplatform.logistics.application.port.outbound.ShipmentDispatchPort;
import com.example.scmplatform.logistics.application.routing.CarrierRouter;
import com.example.scmplatform.logistics.domain.error.ShipmentDispatchException;
import com.example.scmplatform.logistics.domain.model.CarrierCode;
import com.example.scmplatform.logistics.domain.model.Dispatch;
import com.example.scmplatform.logistics.domain.model.TrackingNo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Drive a PENDING / DISPATCH_FAILED dispatch to a vendor selected by the {@link CarrierRouter}
 * from the shipment's {@code requestedCarrierCode} (ADR-053 §D2/§D3). The router picks exactly one
 * vendor per shipment (domestic → 굿스플로; international → EasyPost; null/unmapped → the configured
 * default with a {@code CARRIER_UNROUTABLE} degrade). The use case injects the router — not a
 * {@link ShipmentDispatchPort} directly — because two {@code !standalone} port beans now coexist.
 *
 * <p>Because a {@code DISPATCH_FAILED} dispatch never set {@code vendor}, {@code :retry} re-selects
 * deterministically from the stored {@code requestedCarrierCode} (a null signal re-routes to the
 * default, same as the first attempt — stable, not random).
 *
 * <p><b>Idempotent (S1/S2).</b> An already-{@code DISPATCHED} dispatch short-circuits with the
 * cached ack and <b>no vendor call</b> — the property the operator {@code :retry} relies on.
 * A vendor failure is <b>not</b> propagated: it is recorded {@code DISPATCH_FAILED} and
 * recovered via {@code :retry} (S5 — a failed carrier never blocks or rolls back). The
 * {@code Idempotency-Key = shipment.id} + {@code dispatch_request_dedupe} short-circuit is applied
 * per selected vendor, so a shipment cannot be double-dispatched across vendors (§2.7).
 */
@Service
public class DispatchShipmentUseCase {

    private final CarrierRouter carrierRouter;
    private final DispatchPersistencePort persistencePort;

    public DispatchShipmentUseCase(CarrierRouter carrierRouter,
                                   DispatchPersistencePort persistencePort) {
        this.carrierRouter = carrierRouter;
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
            ShipmentDispatchPort dispatchPort =
                    carrierRouter.select(dispatch.getRequestedCarrierCode());
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
