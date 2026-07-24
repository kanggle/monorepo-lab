package com.example.scmplatform.logistics.domain.error;

/**
 * The {@code CARRIER_UNROUTABLE} error surface (HTTP 422) — reserved for a hard "no vendor at all"
 * condition (e.g. a misconfigured router with no default vendor bean, rejected at construction).
 *
 * <p><b>Routing note (BE-043).</b> A null/unmapped {@code carrierCode} is <b>not</b> thrown as this
 * exception on the dispatch path: the {@code CarrierRouter} routes it to the configured default
 * vendor and emits a {@code CARRIER_UNROUTABLE} <b>degrade</b> (log + metric), never a silent drop
 * and never a dropped shipment (architecture.md § Failure Modes; task Failure Scenario D). This
 * type remains the registered 422 mapping for the error code's surface.
 */
public class CarrierUnroutableException extends RuntimeException {

    public CarrierUnroutableException(String message) {
        super(message);
    }
}
