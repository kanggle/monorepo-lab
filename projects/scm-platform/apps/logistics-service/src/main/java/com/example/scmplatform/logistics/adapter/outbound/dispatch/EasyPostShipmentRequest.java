package com.example.scmplatform.logistics.adapter.outbound.dispatch;

/**
 * Vendor-shaped EasyPost {@code POST /shipments} request body (I8) — <b>package-private</b>,
 * never crosses the {@code ShipmentDispatchPort} boundary.
 *
 * <p>Phase-1 shape: the seam carries shipment identity + line summary but <b>no destination
 * address</b> (the deliberate wms→TMS contract gap, ADR-052 §A2), so Phase 1 is a carrier
 * handover / tracking-registration keyed by the shipment reference — not a full address-bearing
 * label buy (subscription contract § Known input gap). EasyPost wraps the payload under
 * {@code "shipment"}.
 */
record EasyPostShipmentRequest(ShipmentBody shipment) {

    record ShipmentBody(String reference) {
    }

    static EasyPostShipmentRequest of(String reference) {
        return new EasyPostShipmentRequest(new ShipmentBody(reference));
    }
}
