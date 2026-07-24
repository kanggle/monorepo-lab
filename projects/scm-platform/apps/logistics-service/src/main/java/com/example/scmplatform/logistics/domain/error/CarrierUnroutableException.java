package com.example.scmplatform.logistics.domain.error;

/**
 * Raised when no vendor can be resolved for a shipment (a {@code null carrierCode} with no
 * configured default, or an unmapped region). Maps to HTTP 422 {@code CARRIER_UNROUTABLE}.
 *
 * <p><b>Phase-1 note.</b> With one live vendor there is no {@code CarrierRouter}, so this is
 * <b>not</b> thrown by the dispatch path yet — it is the reserved degrade signal the
 * {@code CarrierRouter} (TASK-SCM-BE-043) will raise for an unmapped region rather than
 * silently dropping the shipment (architecture.md § Failure Modes). Registered now so the
 * error surface is stable when routing lands.
 */
public class CarrierUnroutableException extends RuntimeException {

    public CarrierUnroutableException(String message) {
        super(message);
    }
}
