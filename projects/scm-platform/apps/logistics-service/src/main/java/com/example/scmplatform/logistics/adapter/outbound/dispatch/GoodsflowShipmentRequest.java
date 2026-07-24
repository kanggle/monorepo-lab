package com.example.scmplatform.logistics.adapter.outbound.dispatch;

/**
 * Vendor-shaped 굿스플로 {@code POST /shipments} (접수/운송장 발행) request body (I8) —
 * <b>package-private</b>, never crosses the {@code ShipmentDispatchPort} boundary.
 *
 * <p>Phase-1 shape mirrors EasyPost: the seam carries shipment identity + line summary but
 * <b>no destination address</b> (the deliberate wms→TMS contract gap, ADR-052 §A2), so Phase 1
 * is a carrier handover / 운송장 등록 keyed by the shipment reference — not a full address-bearing
 * label buy (subscription contract § Known input gap). 굿스플로 wraps the payload under
 * {@code "shipment"}.
 */
record GoodsflowShipmentRequest(ShipmentBody shipment) {

    record ShipmentBody(String reference) {
    }

    static GoodsflowShipmentRequest of(String reference) {
        return new GoodsflowShipmentRequest(new ShipmentBody(reference));
    }
}
