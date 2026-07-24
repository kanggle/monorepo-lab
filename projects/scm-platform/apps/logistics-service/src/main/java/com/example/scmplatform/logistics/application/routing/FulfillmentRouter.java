package com.example.scmplatform.logistics.application.routing;

import com.example.scmplatform.logistics.domain.model.Dispatch;

/**
 * The multimodal fulfillment seam (ADR-053 §D4). Decides <b>how</b> a confirmed shipment is
 * fulfilled — in Phase 1 always by <b>self-fulfillment</b> (the goods already left a wms warehouse,
 * so the service hands them to a carrier via the {@link CarrierRouter} → {@code ShipmentDispatchPort}).
 *
 * <p><b>Framework-free</b> (no Spring annotations): a pure domain service, constructor-free,
 * fully unit-testable. Wired as a plain {@code @Bean}.
 *
 * <p><b>Phase-1 shape — self-branch only.</b> Every input arrives via the
 * {@code outbound.shipping.confirmed} seam (i.e. wms already fulfilled), so {@link #route(Dispatch)}
 * resolves {@link FulfillmentMode#SELF}. The Phase-2 3PL branch (send a fulfillment request to a 3PL
 * instead of dispatching a wms-shipped parcel) is a <b>documented extension point</b>: the enum
 * carries {@link FulfillmentMode#THIRD_PARTY_LOGISTICS}, but {@code route} never returns it in Phase 1.
 * The 3PL arm attaches here <b>without</b> modifying the dispatch path (ADR-053 §D4) and is gated on
 * <b>ADR-052 §D8-3</b> — it is NOT active routing logic in Phase 1 (task Failure Scenario A).
 */
public class FulfillmentRouter {

    /**
     * How a confirmed shipment is fulfilled.
     *
     * <ul>
     *   <li>{@link #SELF} — Phase 1: wms already fulfilled → carrier dispatch (the only mode
     *       {@link #route(Dispatch)} resolves in Phase 1).</li>
     *   <li>{@link #THIRD_PARTY_LOGISTICS} — <b>Phase 2, ADR-052 §D8-3.</b> The documented
     *       extension point: a 3PL-origin shipment would route to a {@code ThirdPartyFulfillmentPort}.
     *       <b>Inert in Phase 1</b> — never returned by {@code route}; the consume path guards it.</li>
     * </ul>
     */
    public enum FulfillmentMode {
        SELF,
        THIRD_PARTY_LOGISTICS
    }

    /**
     * Resolve the fulfillment mode for a confirmed shipment.
     *
     * <p><b>Phase 1:</b> every input arrives via {@code outbound.shipping.confirmed} (wms already
     * fulfilled), so this always resolves {@link FulfillmentMode#SELF} → carrier dispatch through the
     * {@link CarrierRouter}.
     *
     * <p><b>Phase 2 (ADR-052 §D8-3), extension point — NOT wired here:</b> a 3PL-origin shipment
     * would resolve {@link FulfillmentMode#THIRD_PARTY_LOGISTICS} and route to a
     * {@code ThirdPartyFulfillmentPort} <b>without</b> touching the dispatch path (ADR-053 §D4).
     * Deliberately absent in Phase 1 so this task does not absorb Phase 2.
     */
    public FulfillmentMode route(Dispatch dispatch) {
        return FulfillmentMode.SELF;
    }
}
