package com.example.scmplatform.logistics.application.port.outbound;

import com.example.scmplatform.logistics.domain.model.Dispatch;

/**
 * Outbound port: push a confirmed shipment to a carrier-aggregator vendor (ADR-053 §D2).
 *
 * <p>The domain calls {@code dispatch(dispatch)} without knowing which vendor answers
 * (EasyPost today; 굿스플로 in BE-043; a standalone stub for local/CI) — the swappable-vendor
 * seam that is the reason this service exists (I7). Implementations must translate their
 * vendor DTOs to/from {@link DispatchAck} inside the adapter (I8) and honour the stable
 * {@code Idempotency-Key = shipment.id} (I4).
 */
public interface ShipmentDispatchPort {

    /**
     * Dispatch the shipment to the vendor and return the ack.
     *
     * @throws com.example.scmplatform.logistics.domain.error.ShipmentDispatchException
     *         on a vendor failure (transient exhaustion or permanent 4xx) — the caller records
     *         {@code DISPATCH_FAILED} and recovers via the operator {@code :retry} endpoint.
     */
    DispatchAck dispatch(Dispatch dispatch);
}
